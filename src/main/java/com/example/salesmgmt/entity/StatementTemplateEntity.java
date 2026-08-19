package com.example.salesmgmt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "statement_templates")
public class StatementTemplateEntity {

    @Id
    private Long id;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Lob
    @Column(name = "file_bytes", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] fileBytes;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected StatementTemplateEntity() {
    }

    public StatementTemplateEntity(Long id, String originalFilename, byte[] fileBytes) {
        this.id = id;
        this.originalFilename = originalFilename;
        this.fileBytes = fileBytes;
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String originalFilename, byte[] fileBytes) {
        this.originalFilename = originalFilename;
        this.fileBytes = fileBytes;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public byte[] getFileBytes() {
        return fileBytes;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
