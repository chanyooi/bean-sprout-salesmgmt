package com.example.salesmgmt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(
        name = "monthly_custom_expenses",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_monthly_custom_expense_month_name",
                columnNames = {"month_start", "expense_name"}
        ),
        indexes = @Index(
                name = "idx_monthly_custom_expense_month_order",
                columnList = "month_start,sort_order"
        )
)
public class MonthlyCustomExpenseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Column(name = "expense_name", nullable = false, length = 80)
    private String expenseName;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected MonthlyCustomExpenseEntity() {
    }

    public MonthlyCustomExpenseEntity(
            LocalDate monthStart,
            String expenseName,
            BigDecimal amount,
            int sortOrder
    ) {
        if (monthStart == null) {
            throw new IllegalArgumentException("비용 월을 입력해주세요.");
        }
        this.monthStart = monthStart;
        this.expenseName = normalizeName(expenseName);
        updateAmount(amount);
        this.sortOrder = Math.max(sortOrder, 0);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getMonthStart() {
        return monthStart;
    }

    public String getExpenseName() {
        return expenseName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void updateAmount(BigDecimal amount) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        if (safeAmount.signum() < 0) {
            throw new IllegalArgumentException("비용은 0원 이상이어야 합니다.");
        }
        this.amount = safeAmount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("추가 비용 항목명을 입력해주세요.");
        }
        String normalized = value.trim();
        if (normalized.length() > 80) {
            throw new IllegalArgumentException("추가 비용 항목명은 80자 이하로 입력해주세요.");
        }
        return normalized;
    }
}
