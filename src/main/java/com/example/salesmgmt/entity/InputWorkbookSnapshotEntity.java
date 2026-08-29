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
     * Railway의 로컬 파일시스템은 재배포/재시작 때 사라질 수 있으므로
     * 가장 최근에 성공적으로 저장한 input_data.xlsx 원본을 DB에 보관합니다.
     * Base64를 LONGTEXT로 저장해 MySQL BLOB 크기 제약과 테스트 DB 차이를 피합니다.
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
            String fileBase64
    ) {
        this.monthKey = monthKey;
        replace(fileName, fileSize, fileBase64);
    }

    public void replace(
            String fileName,
            long fileSize,
            String fileBase64
    ) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileBase64 = fileBase64;
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getMonthKey() { return monthKey; }
    public String getFileName() { return fileName; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public long getFileSize() { return fileSize; }
    public String getFileBase64() { return fileBase64; }
}
