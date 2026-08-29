package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.StatementDeliveryMethod;
import com.example.salesmgmt.domain.StatementWorkbookResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StatementGenerationJobService {

    private static final Logger log = LoggerFactory.getLogger(StatementGenerationJobService.class);
    private static final Duration RETENTION = Duration.ofMinutes(30);

    private final StatementWorkbookOnePassService onePassService;
    private final FilteredStatementWorkbookService filteredService;
    private final StatementFinalBillingPatchService finalBillingPatchService;
    private final HeavyFileTaskExecutor heavyFileTaskExecutor;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public StatementGenerationJobService(
            StatementWorkbookOnePassService onePassService,
            FilteredStatementWorkbookService filteredService,
            StatementFinalBillingPatchService finalBillingPatchService,
            HeavyFileTaskExecutor heavyFileTaskExecutor
    ) {
        this.onePassService = onePassService;
        this.filteredService = filteredService;
        this.finalBillingPatchService = finalBillingPatchService;
        this.heavyFileTaskExecutor = heavyFileTaskExecutor;
    }

    public String start(
            MultipartFile templateFile,
            YearMonth month,
            boolean includeEmpty,
            StatementDeliveryMethod deliveryMethod
    ) {
        cleanupExpired();

        String id = UUID.randomUUID().toString();
        Job job = new Job();
        jobs.put(id, job);

        heavyFileTaskExecutor.submit(() -> {
            long started = System.nanoTime();
            try {
                StatementWorkbookResult generated = deliveryMethod == null
                        ? onePassService.generate(templateFile, month, includeEmpty)
                        : filteredService.generate(
                                templateFile,
                                month,
                                includeEmpty,
                                deliveryMethod
                        );

                // Excel이 파일을 열 때 수식을 다시 계산하기를 기다리지 않고,
                // DB의 실제 판매금액 합계로 각 시트의 최종 청구금액을 확정한다.
                generated = finalBillingPatchService.patchMonthly(
                        generated,
                        month
                );

                Path file = Files.createTempFile("statement-download-", ".xlsx");
                try {
                    Files.write(file, generated.fileBytes());
                } catch (IOException | RuntimeException exception) {
                    deleteQuietly(file);
                    throw exception;
                }

                JobFileResult result = new JobFileResult(
                        file,
                        generated.filename(),
                        generated.generatedSheetCount(),
                        generated.sheetWithSalesCount(),
                        generated.warningCount()
                );
                job.complete(result);

                long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
                log.info(
                        "Statement generation completed. jobId={}, month={}, deliveryMethod={}, elapsedMs={}, bytes={}",
                        id,
                        month,
                        deliveryMethod,
                        elapsedMs,
                        safeSize(file)
                );
            } catch (Exception exception) {
                job.fail(safeMessage(exception));
                log.error(
                        "Statement generation failed. jobId={}, month={}, deliveryMethod={}",
                        id,
                        month,
                        deliveryMethod,
                        exception
                );
            }
        });

        return id;
    }

    public JobSnapshot status(String id) {
        cleanupExpired();
        Job job = jobs.get(id);
        if (job == null) {
            return new JobSnapshot(JobState.NOT_FOUND, null, "작업을 찾을 수 없습니다.");
        }
        return new JobSnapshot(
                job.state,
                job.result == null ? null : job.result.filename(),
                job.error
        );
    }

    public JobFileResult result(String id) {
        cleanupExpired();
        Job job = jobs.get(id);
        if (job == null || job.state != JobState.READY || job.result == null) {
            return null;
        }
        if (!Files.exists(job.result.path())) {
            job.fail("생성된 명세서 파일을 찾을 수 없습니다. 다시 생성해주세요.");
            return null;
        }
        job.updatedAt = Instant.now();
        return job.result;
    }

    private void cleanupExpired() {
        Instant cutoff = Instant.now().minus(RETENTION);
        jobs.entrySet().removeIf(entry -> {
            Job job = entry.getValue();
            if (!job.updatedAt.isBefore(cutoff)) {
                return false;
            }
            if (job.result != null) {
                deleteQuietly(job.result.path());
            }
            return true;
        });
    }

    private long safeSize(Path path) {
        try {
            return path == null ? 0L : Files.size(path);
        } catch (IOException exception) {
            return 0L;
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "명세서를 생성하지 못했습니다."
                : message;
    }

    @PreDestroy
    public void cleanupFiles() {
        jobs.values().forEach(job -> {
            if (job.result != null) {
                deleteQuietly(job.result.path());
            }
        });
    }

    public enum JobState {
        RUNNING,
        READY,
        FAILED,
        NOT_FOUND
    }

    public record JobSnapshot(
            JobState state,
            String filename,
            String error
    ) {
    }

    public record JobFileResult(
            Path path,
            String filename,
            int generatedSheetCount,
            int sheetWithSalesCount,
            int warningCount
    ) {
    }

    private static final class Job {
        private volatile JobState state = JobState.RUNNING;
        private volatile JobFileResult result;
        private volatile String error;
        private volatile Instant updatedAt = Instant.now();

        private void complete(JobFileResult result) {
            this.result = result;
            this.state = JobState.READY;
            this.updatedAt = Instant.now();
        }

        private void fail(String error) {
            if (this.result != null) {
                try {
                    Files.deleteIfExists(this.result.path());
                } catch (IOException ignored) {
                }
                this.result = null;
            }
            this.error = error;
            this.state = JobState.FAILED;
            this.updatedAt = Instant.now();
        }
    }
}
