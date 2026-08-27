package com.example.salesmgmt.entity;

import com.example.salesmgmt.domain.ExpenseCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(
        name = "monthly_expense_items",
        indexes = @Index(name = "idx_monthly_expense_items_month", columnList = "month_start")
)
public class MonthlyExpenseItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private ExpenseCategory category;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    protected MonthlyExpenseItemEntity() {
    }

    public MonthlyExpenseItemEntity(
            LocalDate monthStart,
            ExpenseCategory category,
            String itemName,
            BigDecimal amount
    ) {
        this.monthStart = monthStart;
        update(category, itemName, amount);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getMonthStart() {
        return monthStart;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public String getItemName() {
        return itemName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void update(ExpenseCategory category, String itemName, BigDecimal amount) {
        if (category == null) {
            throw new IllegalArgumentException("비용 분류를 선택해주세요.");
        }

        String safeName = itemName == null ? "" : itemName.trim();
        if (safeName.isBlank()) {
            throw new IllegalArgumentException("비용 항목명을 입력해주세요.");
        }
        if (safeName.length() > 100) {
            throw new IllegalArgumentException("비용 항목명은 100자 이내로 입력해주세요.");
        }

        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        if (safeAmount.signum() < 0) {
            throw new IllegalArgumentException("비용은 0원 이상이어야 합니다.");
        }

        this.category = category;
        this.itemName = safeName;
        this.amount = safeAmount.setScale(2, RoundingMode.HALF_UP);
    }
}
