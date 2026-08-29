package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.OrderSnapshot;
import com.example.salesmgmt.entity.InputWorkbookSnapshotEntity;
import com.example.salesmgmt.repository.InputWorkbookSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class InputWorkbookSnapshotService {

    private static final long MAX_SNAPSHOT_BYTES = 20L * 1024L * 1024L;

    private final InputWorkbookSnapshotRepository repository;

    public InputWorkbookSnapshotService(InputWorkbookSnapshotRepository repository) {
        this.repository = repository;
    }

    /**
     * DB 저장까지 성공한 input_data.xlsx를 수정하지 않고 그대로 보관합니다.
     * 같은 월을 다시 업로드하면 그 월의 '최근 성공 원본'만 교체합니다.
     */
    @Transactional
    public void storeLatestUploadedWorkbook(
            Path uploadedFile,
            String originalFilename,
            List<OrderSnapshot> orderSnapshots
    ) {
        if (uploadedFile == null || orderSnapshots == null || orderSnapshots.isEmpty()) {
            return;
        }

        Set<YearMonth> months = new LinkedHashSet<>();
        for (OrderSnapshot snapshot : orderSnapshots) {
            if (snapshot != null && snapshot.deliveryDate() != null) {
                months.add(YearMonth.from(snapshot.deliveryDate()));
            }
        }
        if (months.isEmpty()) {
            return;
        }

        byte[] bytes;
        try {
            long size = Files.size(uploadedFile);
            if (size <= 0) {
                throw new IllegalArgumentException("업로드 원본 파일이 비어 있습니다.");
            }
            if (size > MAX_SNAPSHOT_BYTES) {
                throw new IllegalArgumentException(
                        "업로드 원본이 20MB를 초과해 장부 원본 보관을 할 수 없습니다."
                );
            }
            bytes = Files.readAllBytes(uploadedFile);
        } catch (IOException exception) {
            throw new IllegalStateException("업로드 원본 장부를 보관하지 못했습니다.", exception);
        }

        String fileName = safeFilename(originalFilename);
        String base64 = Base64.getEncoder().encodeToString(bytes);

        for (YearMonth month : months) {
            String monthKey = month.toString();
            InputWorkbookSnapshotEntity entity = repository.findByMonthKey(monthKey)
                    .orElseGet(() -> new InputWorkbookSnapshotEntity(
                            monthKey,
                            fileName,
                            bytes.length,
                            base64
                    ));

            if (entity.getId() != null) {
                entity.replace(fileName, bytes.length, base64);
            }
            repository.save(entity);
        }
    }

    @Transactional(readOnly = true)
    public Optional<StoredWorkbook> findLatest(YearMonth month) {
        if (month == null) {
            return Optional.empty();
        }

        return repository.findByMonthKey(month.toString())
                .map(entity -> new StoredWorkbook(
                        entity.getFileName(),
                        entity.getUploadedAt(),
                        entity.getFileSize(),
                        Base64.getDecoder().decode(entity.getFileBase64())
                ));
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "input_data.xlsx";
        }
        String trimmed = filename.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    public record StoredWorkbook(
            String filename,
            java.time.LocalDateTime uploadedAt,
            long fileSize,
            byte[] bytes
    ) {
    }
}
