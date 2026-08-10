package com.example.salesmgmt.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "statement_delivery_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_statement_delivery_log_vendor_month",
                columnNames = {"vendor_id", "month_key"}
        )
)
public class StatementDeliveryLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorEntity vendor;

    @Column(name = "month_key", nullable = false, length = 7)
    private String monthKey;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    protected StatementDeliveryLogEntity() {}

    public StatementDeliveryLogEntity(
            VendorEntity vendor,
            String monthKey
    ) {
        this.vendor = vendor;
        this.monthKey = monthKey;
        this.sentAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public VendorEntity getVendor() { return vendor; }
    public String getMonthKey() { return monthKey; }
    public LocalDateTime getSentAt() { return sentAt; }
}
