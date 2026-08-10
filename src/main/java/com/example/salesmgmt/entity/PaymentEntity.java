package com.example.salesmgmt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorEntity vendor;

    @Column(name = "settlement_month", nullable = false, length = 7)
    private String settlementMonth;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PaymentEntity() {
    }

    public PaymentEntity(
            VendorEntity vendor,
            String settlementMonth,
            LocalDate paymentDate,
            BigDecimal amount,
            String note
    ) {
        if (vendor == null) {
            throw new IllegalArgumentException("거래처가 필요합니다.");
        }
        if (settlementMonth == null || settlementMonth.isBlank()) {
            throw new IllegalArgumentException("정산월이 필요합니다.");
        }
        if (paymentDate == null) {
            throw new IllegalArgumentException("입금일이 필요합니다.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("입금액은 0원보다 커야 합니다.");
        }

        this.vendor = vendor;
        this.settlementMonth = settlementMonth;
        this.paymentDate = paymentDate;
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.note = blankToNull(note);
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public VendorEntity getVendor() {
        return vendor;
    }

    public String getSettlementMonth() {
        return settlementMonth;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
