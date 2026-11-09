package com.example.salesmgmt.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "standard_statement_values",
    uniqueConstraints = @UniqueConstraint(name = "uk_standard_statement_year_type_code",
        columnNames = {"statement_year", "statement_type", "account_code"}))
public class StandardStatementValueEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "statement_year", nullable = false)
    private int year;

    @Column(name = "statement_type", nullable = false, length = 12)
    private String type;

    @Column(name = "account_code", nullable = false, length = 2)
    private String code;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    protected StandardStatementValueEntity() {}

    public StandardStatementValueEntity(int year, String type, String code, BigDecimal amount) {
        this.year = year;
        this.type = type;
        this.code = code;
        this.amount = amount;
    }

    public int getYear() { return year; }
    public String getType() { return type; }
    public String getCode() { return code; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
