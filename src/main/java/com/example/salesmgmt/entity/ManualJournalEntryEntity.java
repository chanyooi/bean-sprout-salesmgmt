package com.example.salesmgmt.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "manual_journal_entries", indexes = @Index(name = "idx_manual_journal_date", columnList = "entry_date"))
public class ManualJournalEntryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "description", nullable = false, length = 240)
    private String description;

    @Column(name = "debit_account", nullable = false, length = 60)
    private String debitAccount;

    @Column(name = "credit_account", nullable = false, length = 60)
    private String creditAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    protected ManualJournalEntryEntity() {}

    public ManualJournalEntryEntity(LocalDate entryDate, String description,
                                    String debitAccount, String creditAccount, BigDecimal amount) {
        this.entryDate = entryDate;
        this.description = description;
        this.debitAccount = debitAccount;
        this.creditAccount = creditAccount;
        this.amount = amount;
    }

    public Long getId() { return id; }
    public LocalDate getEntryDate() { return entryDate; }
    public String getDescription() { return description; }
    public String getDebitAccount() { return debitAccount; }
    public String getCreditAccount() { return creditAccount; }
    public BigDecimal getAmount() { return amount; }
}
