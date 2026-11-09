package com.example.salesmgmt.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "manual_bookkeeping_monthly",
       uniqueConstraints = @UniqueConstraint(name = "uk_manual_bookkeeping_month_category",
               columnNames = {"entry_year", "entry_month", "category_code"}))
public class ManualBookkeepingMonthlyEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_year", nullable = false)
    private int year;

    @Column(name = "entry_month", nullable = false)
    private int month;

    @Column(name = "category_code", nullable = false, length = 40)
    private String categoryCode;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    protected ManualBookkeepingMonthlyEntity() {}

    public ManualBookkeepingMonthlyEntity(int year, int month, String categoryCode, BigDecimal amount) {
        this.year = year;
        this.month = month;
        this.categoryCode = categoryCode;
        this.amount = amount;
    }

    public Long getId() { return id; }
    public int getYear() { return year; }
    public int getMonth() { return month; }
    public String getCategoryCode() { return categoryCode; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
