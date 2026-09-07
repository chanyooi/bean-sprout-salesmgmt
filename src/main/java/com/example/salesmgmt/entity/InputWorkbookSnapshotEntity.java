package com.example.salesmgmt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "input_workbook_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_input_workbook_snapshot_month",
                columnNames = "month_key"
        )
)
public class InputWorkbookSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_key", nullable = false, length = 7)
    private String monthKey;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    /**
     * 새 업로드는 원본 바이트를 그대로 LONGBLOB에 저장합니다.
     * Base64 문자열을 만들지 않아 업로드 직후 메모리 피크를 줄입니다.
     */
    @Lob
    @Column(name = "file_data", columnDefinition = "LONGBLOB")
    private byte[] fileData;

    /**
     * 기존 배포에서 저장한 원본과의 하위 호환용 컬럼입니다.
     * 새 저장에서는 빈 문자열만 남기고, 읽을 때 fileData가 없으면 이 값을 사용합니다.
     */
    @Lob
    @Column(name = "file_base64", nullable = false, columnDefinition = "LONGTEXT")
    private String fileBase64;

    protected InputWorkbookSnapshotEntity() {
    }

    public InputWorkbookSnapshotEntity(
            String monthKey,
            String fileName,
            long fileSize,
            byte[] fileData
    ) {
        this.monthKey = monthKey;
        replace(fileName, fileSize, fileData);
    }

    public void replace(
            String fileName,
            long fileSize,
            byte[] fileData
    ) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileData = fileData;
        this.fileBase64 = "";
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getMonthKey() { return monthKey; }
    public String getFileName() { return fileName; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public long getFileSize() { return fileSize; }
    public byte[] getFileData() { return fileData; }
    public String getFileBase64() { return fileBase64; }
}
