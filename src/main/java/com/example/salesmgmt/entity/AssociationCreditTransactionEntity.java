package com.example.salesmgmt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "association_credit_transactions",
        indexes = @Index(
                name = "idx_association_credit_transaction_date",
                columnList = "transaction_date"
        )
)
public class AssociationCreditTransactionEntity {

    public enum TransactionType {
        CREDIT,
        PAYMENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AssociationCreditTransactionEntity() {
    }

    public AssociationCreditTransactionEntity(
            LocalDate transactionDate,
            TransactionType transactionType,
            BigDecimal amount,
            String note
    ) {
        if (transactionDate == null) {
            throw new IllegalArgumentException("날짜를 입력해주세요.");
        }
        if (transactionType == null) {
            throw new IllegalArgumentException("거래 구분을 선택해주세요.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("금액은 0원보다 커야 합니다.");
        }

        this.transactionDate = transactionDate;
        this.transactionType = transactionType;
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.note = blankToNull(note);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public TransactionType getTransactionType() {
        return transactionType;
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

    public BigDecimal getSignedAmount() {
        if (transactionType == TransactionType.PAYMENT) {
            return amount.negate();
        }
        return amount;
    }

    public String getTypeLabel() {
        return transactionType == TransactionType.CREDIT ? "외상" : "입금";
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
