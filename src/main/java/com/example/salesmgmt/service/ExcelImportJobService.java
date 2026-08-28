package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.ExcelImportResult;
import com.example.salesmgmt.domain.SaveResult;
import com.example.salesmgmt.exception.SalesDataConflictException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ExcelImportJobService {

    private static final Logger log = LoggerFactory.getLogger(ExcelImportJobService.class);
    private static final Duration JOB_RETENTION = Duration.ofMinutes(30);

    private final ExcelImportService excelImportService;
    private final SalesPersistenceService salesPersistenceService;
    private final UploadHistoryService uploadHistoryService;
    private final Map<String, JobView> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "excel-import-worker");
        thread.setDaemon(true);
        return thread;
    });

    public ExcelImportJobService(
            ExcelImportService excelImportService,
            SalesPersistenceService salesPersistenceService,
            UploadHistoryService uploadHistoryService
    ) {
        this.excelImportService = excelImportService;
        this.salesPersistenceService = salesPersistenceService;
        this.uploadHistoryService = uploadHistoryService;
    }

    /**
     * Railway의 HTTP 요청을 엑셀 파싱/DB 동기화가 끝날 때까지 붙잡아두지 않는다.
     * 요청 안에서는 업로드 파일을 임시 파일로 복사한 뒤 즉시 job id를 반환하고,
     * 실제 무거운 처리는 단일 백그라운드 워커에서 순서대로 실행한다.
     */
    public String start(MultipartFile file, String action) {
        validateFile(file);
        cleanupExpiredJobs();

        String normalizedAction = "save".equals(action) ? "save" : "preview";
        String originalFilename = safeFilename(file.getOriginalFilename());
        Path tempFile;

        try {
            tempFile = Files.createTempFile("sales-upload-", ".xlsx");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("업로드 파일을 임시 저장하지 못했습니다. 다시 시도해주세요.", exception);
        }

        String jobId = UUID.randomUUID().toString();
        jobs.put(jobId, JobView.processing(jobId, normalizedAction, originalFilename));
        executor.submit(() -> process(jobId, normalizedAction, originalFilename, tempFile));
        return jobId;
    }

    public Optional<JobView> find(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return Optional.empty();
        }
        cleanupExpiredJobs();
        return Optional.ofNullable(jobs.get(jobId));
    }

    private void process(
            String jobId,
            String action,
            String originalFilename,
            Path tempFile
    ) {
        try {
            MultipartFile storedFile = new StoredMultipartFile(tempFile, originalFilename);
            ExcelImportResult result = excelImportService.importInputData(storedFile);

            if (!"save".equals(action)) {
                jobs.put(jobId, JobView.completed(
                        jobId,
                        action,
                        originalFilename,
                        result,
                        null,
                        null,
                        null
                ));
                return;
            }

            if (result.errorCount() > 0) {
                jobs.put(jobId, JobView.completed(
                        jobId,
                        action,
                        originalFilename,
                        result,
                        null,
                        "엑셀 오류가 있어 저장하지 않았습니다. 오류를 수정한 뒤 다시 올려주세요.",
                        null
                ));
                return;
            }

            if (result.orderSnapshots().isEmpty()) {
                jobs.put(jobId, JobView.completed(
                        jobId,
                        action,
                        originalFilename,
                        result,
                        null,
                        "동기화할 거래처 행이 없습니다.",
                        null
                ));
                return;
            }

            String beforeSnapshot = uploadHistoryService.captureSalesSnapshot();
            SaveResult saveResult = salesPersistenceService.save(
                    result.records(),
                    result.orderSnapshots()
            );
            uploadHistoryService.recordSuccess(
                    originalFilename,
                    beforeSnapshot,
                    saveResult
            );

            // 저장 성공 뒤에는 대용량 변환 목록을 메모리에 계속 들고 있지 않는다.
            jobs.put(jobId, JobView.completed(
                    jobId,
                    action,
                    originalFilename,
                    null,
                    saveResult,
                    null,
                    null
            ));
        } catch (SalesDataConflictException exception) {
            jobs.put(jobId, JobView.failed(jobId, action, originalFilename, null, exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            jobs.put(jobId, JobView.failed(jobId, action, originalFilename, exception.getMessage(), null));
        } catch (RuntimeException exception) {
            log.error("비동기 엑셀 업로드 처리 실패. jobId={}", jobId, exception);
            jobs.put(jobId, JobView.failed(
                    jobId,
                    action,
                    originalFilename,
                    "업로드 처리 중 서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
                    null
            ));
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException exception) {
                log.warn("엑셀 업로드 임시 파일을 삭제하지 못했습니다: {}", tempFile, exception);
            }
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 input_data.xlsx 파일을 선택해주세요.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException(".xlsx 형식의 파일만 업로드할 수 있습니다.");
        }
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "input_data.xlsx";
        }
        return filename.length() > 255 ? filename.substring(0, 255) : filename;
    }

    private void cleanupExpiredJobs() {
        Instant cutoff = Instant.now().minus(JOB_RETENTION);
        jobs.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(cutoff));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public enum JobState {
        PROCESSING,
        COMPLETED,
        FAILED
    }

    public record JobView(
            String id,
            JobState state,
            String action,
            String originalFilename,
            Instant createdAt,
            ExcelImportResult result,
            SaveResult saveResult,
            String fatalError,
            String saveError
    ) {
        private static JobView processing(String id, String action, String filename) {
            return new JobView(
                    id,
                    JobState.PROCESSING,
                    action,
                    filename,
                    Instant.now(),
                    null,
                    null,
                    null,
                    null
            );
        }

        private static JobView completed(
                String id,
                String action,
                String filename,
                ExcelImportResult result,
                SaveResult saveResult,
                String saveError,
                String fatalError
        ) {
            return new JobView(
                    id,
                    JobState.COMPLETED,
                    action,
                    filename,
                    Instant.now(),
                    result,
                    saveResult,
                    fatalError,
                    saveError
            );
        }

        private static JobView failed(
                String id,
                String action,
                String filename,
                String fatalError,
                String saveError
        ) {
            return new JobView(
                    id,
                    JobState.FAILED,
                    action,
                    filename,
                    Instant.now(),
                    null,
                    null,
                    fatalError,
                    saveError
            );
        }

        public boolean finished() {
            return state != JobState.PROCESSING;
        }
    }

    private static final class StoredMultipartFile implements MultipartFile {
        private final Path path;
        private final String originalFilename;

        private StoredMultipartFile(Path path, String originalFilename) {
            this.path = path;
            this.originalFilename = originalFilename;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }

        @Override
        public boolean isEmpty() {
            try {
                return Files.size(path) == 0;
            } catch (IOException exception) {
                return true;
            }
        }

        @Override
        public long getSize() {
            try {
                return Files.size(path);
            } catch (IOException exception) {
                return 0L;
            }
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
