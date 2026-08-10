package com.example.salesmgmt.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "upload_histories")
public class UploadHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "created_vendors", nullable = false)
    private int createdVendors;

    @Column(name = "created_orders", nullable = false)
    private int createdOrders;

    @Column(name = "saved_items", nullable = false)
    private int savedItems;

    @Column(name = "updated_items", nullable = false)
    private int updatedItems;

    @Column(name = "deleted_items", nullable = false)
    private int deletedItems;

    @Column(name = "deleted_orders", nullable = false)
    private int deletedOrders;

    @Column(name = "skipped_items", nullable = false)
    private int skippedItems;

    @Lob
    @Column(name = "before_snapshot_json", nullable = false, columnDefinition = "LONGTEXT")
    private String beforeSnapshotJson;

    @Column(name = "restored", nullable = false)
    private boolean restored;

    @Column(name = "restored_at")
    private LocalDateTime restoredAt;

    protected UploadHistoryEntity() {}

    public UploadHistoryEntity(
            String fileName,
            int createdVendors,
            int createdOrders,
            int savedItems,
            int updatedItems,
            int deletedItems,
            int deletedOrders,
            int skippedItems,
            String beforeSnapshotJson
    ) {
        this.fileName = fileName;
        this.uploadedAt = LocalDateTime.now();
        this.createdVendors = createdVendors;
        this.createdOrders = createdOrders;
        this.savedItems = savedItems;
        this.updatedItems = updatedItems;
        this.deletedItems = deletedItems;
        this.deletedOrders = deletedOrders;
        this.skippedItems = skippedItems;
        this.beforeSnapshotJson = beforeSnapshotJson;
    }

    public Long getId() { return id; }
    public String getFileName() { return fileName; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public int getCreatedVendors() { return createdVendors; }
    public int getCreatedOrders() { return createdOrders; }
    public int getSavedItems() { return savedItems; }
    public int getUpdatedItems() { return updatedItems; }
    public int getDeletedItems() { return deletedItems; }
    public int getDeletedOrders() { return deletedOrders; }
    public int getSkippedItems() { return skippedItems; }
    public String getBeforeSnapshotJson() { return beforeSnapshotJson; }
    public boolean isRestored() { return restored; }
    public LocalDateTime getRestoredAt() { return restoredAt; }

    public void markRestored() {
        this.restored = true;
        this.restoredAt = LocalDateTime.now();
    }
}
