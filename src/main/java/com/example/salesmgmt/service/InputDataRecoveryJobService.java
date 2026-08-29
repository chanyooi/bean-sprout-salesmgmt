package com.example.salesmgmt.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InputDataRecoveryJobService {

    private static final Logger log = LoggerFactory.getLogger(InputDataRecoveryJobService.class);
    private static final Duration RETENTION = Duration.ofMinutes(30);

    private final InputDataRecoveryWorkbookService workbookService;
    private final HeavyFileTaskExecutor heavyFileTaskExecutor;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public InputDataRecoveryJobService(
            InputDataRecoveryWorkbookService workbookService,
            HeavyFileTaskExecutor heavyFileTaskExecutor
    ) {
        this.workbookService = workbookService;
        this.heavyFileTaskExecutor = heavyFileTaskExecutor;
    }

    public String start(YearMonth month, LocalDate endDate) {
        cleanupExpired();

        String id = UUID.randomUUID().toString();
        Job job = new Job(month, endDate);
        jobs.put(id, job);
        heavyFileTaskExecutor.submit(() -> generate(id, job));
        return id;
    }

    public JobSnapshot status(String id) {
        cleanupExpired();
        Job job = jobs.get(id);
        if (job == null) {
            return new JobSnapshot(JobState.NOT_FOUND, null, "작업을 찾을 수 없습니다.");
        }
        return new JobSnapshot(job.state, job.filename, job.error);
    }

    public Path resultPath(String id) {
        cleanupExpired();
        Job job = jobs.get(id);
        if (job == null || job.state != JobState.READY || job.path == null) {
            return null;
        }
        if (!Files.exists(job.path)) {
            job.fail("복구된 장부 파일을 찾을 수 없습니다. 다시 시도해주세요.");
            return null;
        }
        job.updatedAt = Instant.now();
        return job.path;
    }

    public String filename(String id) {
        Job job = jobs.get(id);
        return job == null ? null : job.filename;
    }

    private void generate(String id, Job job) {
        long started = System.nanoTime();
        try {
            Path path = workbookService.createRecoveryWorkbookFile(job.month, job.endDate);
            String filename = "input_data_복구_" + job.endDate + ".xlsx";
            job.complete(path, filename);

            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            log.info(
                    "Input data recovery completed. jobId={}, month={}, through={}, elapsedMs={}, bytes={}",
                    id,
                    job.month,
                    job.endDate,
                    elapsedMs,
                    safeSize(path)
            );
        } catch (RuntimeException exception) {
            job.fail(safeMessage(exception));
            log.error(
                    "Input data recovery failed. jobId={}, month={}, through={}",
                    id,
                    job.month,
                    job.endDate,
                    exception
            );
        }
    }

    private void cleanupExpired() {
        Instant cutoff = Instant.now().minus(RETENTION);
        jobs.entrySet().removeIf(entry -> {
            Job job = entry.getValue();
            if (!job.updatedAt.isBefore(cutoff)) {
                return false;
            }
            deleteQuietly(job.path);
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

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "현재 장부를 복구하지 못했습니다."
                : message;
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

    @PreDestroy
    public void cleanupFiles() {
        jobs.values().forEach(job -> deleteQuietly(job.path));
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

    private static final class Job {
        private final YearMonth month;
        private final LocalDate endDate;
        private volatile JobState state = JobState.RUNNING;
        private volatile Path path;
        private volatile String filename;
        private volatile String error;
        private volatile Instant updatedAt = Instant.now();

        private Job(YearMonth month, LocalDate endDate) {
            this.month = month;
            this.endDate = endDate;
        }

        private void complete(Path path, String filename) {
            this.path = path;
            this.filename = filename;
            this.state = JobState.READY;
            this.updatedAt = Instant.now();
        }

        private void fail(String error) {
            this.error = error;
            this.state = JobState.FAILED;
            this.updatedAt = Instant.now();
        }
    }
}
