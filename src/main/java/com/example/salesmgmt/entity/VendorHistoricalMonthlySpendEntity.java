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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "vendor_historical_monthly_spend",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vendor_historical_spend_vendor_month",
                columnNames = {"vendor_id", "spend_month"}
        )
)
public class VendorHistoricalMonthlySpendEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorEntity vendor;

    @Column(name = "spend_month", nullable = false)
    private LocalDate spendMonth;

    @Column(name = "amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal amount;

    protected VendorHistoricalMonthlySpendEntity() {
    }

    public VendorHistoricalMonthlySpendEntity(
            VendorEntity vendor,
            LocalDate spendMonth,
            BigDecimal amount
    ) {
        this.vendor = vendor;
        this.spendMonth = spendMonth;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public VendorEntity getVendor() {
        return vendor;
    }

    public LocalDate getSpendMonth() {
        return spendMonth;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void updateAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
