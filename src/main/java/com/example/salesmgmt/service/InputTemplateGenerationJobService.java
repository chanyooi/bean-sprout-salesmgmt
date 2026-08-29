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
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InputTemplateGenerationJobService {

    private static final Logger log = LoggerFactory.getLogger(InputTemplateGenerationJobService.class);
    private static final Duration RETENTION = Duration.ofMinutes(30);

    private final InputTemplateWorkbookService workbookService;
    private final HeavyFileTaskExecutor heavyFileTaskExecutor;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<YearMonth, String> activeByMonth = new ConcurrentHashMap<>();

    public InputTemplateGenerationJobService(
            InputTemplateWorkbookService workbookService,
            HeavyFileTaskExecutor heavyFileTaskExecutor
    ) {
        this.workbookService = workbookService;
        this.heavyFileTaskExecutor = heavyFileTaskExecutor;
    }

    public synchronized String start(YearMonth month) {
        cleanupExpired();

        String existingId = activeByMonth.get(month);
        if (existingId != null) {
            Job existing = jobs.get(existingId);
            if (existing != null
                    && existing.state != JobState.FAILED
                    && (existing.path == null || Files.exists(existing.path))) {
                return existingId;
            }
            activeByMonth.remove(month, existingId);
        }

        String id = UUID.randomUUID().toString();
        Job job = new Job(month);
        jobs.put(id, job);
        activeByMonth.put(month, id);

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
            job.fail("생성된 파일을 찾을 수 없습니다. 다시 다운로드해주세요.");
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
            Path path = workbookService.createBlankWorkbookFile(job.month);
            String filename = "input_data_" + job.month + ".xlsx";
            job.complete(path, filename);

            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            log.info(
                    "Input template generation completed. jobId={}, month={}, elapsedMs={}, bytes={}",
                    id,
                    job.month,
                    elapsedMs,
                    safeSize(path)
            );
        } catch (RuntimeException exception) {
            job.fail(safeMessage(exception));
            activeByMonth.remove(job.month, id);
            log.error(
                    "Input template generation failed. jobId={}, month={}",
                    id,
                    job.month,
                    exception
            );
        }
    }

    private synchronized void cleanupExpired() {
        Instant cutoff = Instant.now().minus(RETENTION);
        jobs.entrySet().removeIf(entry -> {
            Job job = entry.getValue();
            if (!job.updatedAt.isBefore(cutoff)) {
                return false;
            }
            deleteQuietly(job.path);
            activeByMonth.remove(job.month, entry.getKey());
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
                ? "input_data.xlsx를 생성하지 못했습니다."
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
        private volatile JobState state = JobState.RUNNING;
        private volatile Path path;
        private volatile String filename;
        private volatile String error;
        private volatile Instant updatedAt = Instant.now();

        private Job(YearMonth month) {
            this.month = month;
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
