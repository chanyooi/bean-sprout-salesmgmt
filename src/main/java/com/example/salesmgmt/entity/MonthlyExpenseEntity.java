package com.example.salesmgmt.entity;

import com.example.salesmgmt.domain.ExpenseType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(
        name = "monthly_expenses",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_monthly_expense_month_type",
                columnNames = {"month_start", "expense_type"}
        )
)
public class MonthlyExpenseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_type", nullable = false, length = 40)
    private ExpenseType expenseType;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    protected MonthlyExpenseEntity() {
    }

    public MonthlyExpenseEntity(
            LocalDate monthStart,
            ExpenseType expenseType,
            BigDecimal amount
    ) {
        this.monthStart = monthStart;
        this.expenseType = expenseType;
        updateAmount(amount);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getMonthStart() {
        return monthStart;
    }

    public ExpenseType getExpenseType() {
        return expenseType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void updateAmount(BigDecimal amount) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        if (safeAmount.signum() < 0) {
            throw new IllegalArgumentException("비용은 0원 이상이어야 합니다.");
        }
        this.amount = safeAmount.setScale(2, RoundingMode.HALF_UP);
    }
}
