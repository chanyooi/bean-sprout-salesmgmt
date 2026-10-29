package com.example.salesmgmt.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name="personal_bank_transactions", uniqueConstraints=@UniqueConstraint(name="uk_personal_bank_fingerprint",columnNames="fingerprint"))
public class PersonalBankTransactionEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false,length=128) private String fingerprint;
    @Column(nullable=false,length=80) private String account;
    @Column(nullable=false) private LocalDate transactionDate;
    @Column(nullable=false,precision=18,scale=2) private BigDecimal incoming;
    @Column(nullable=false,precision=18,scale=2) private BigDecimal outgoing;
    @Column(length=255) private String counterparty;
    @Column(length=255) private String description;
    @Column(length=80) private String category="미분류";
    private Long vendorId;
    @Column(length=7) private String settlementMonth;
    @Column(nullable=false) private boolean reconciled=false;
    protected PersonalBankTransactionEntity() {}
    public PersonalBankTransactionEntity(String fingerprint,String account,LocalDate date,BigDecimal incoming,BigDecimal outgoing,String counterparty,String description) {
        this.fingerprint=fingerprint;this.account=account;this.transactionDate=date;
        this.incoming=incoming;this.outgoing=outgoing;this.counterparty=counterparty;this.description=description;
    }
    public Long getId(){return id;}
    public String getAccount(){return account;}
    public LocalDate getTransactionDate(){return transactionDate;}
    public BigDecimal getIncoming(){return incoming;}
    public BigDecimal getOutgoing(){return outgoing;}
    public String getCounterparty(){return counterparty;}
    public String getDescription(){return description;}
    public String getCategory(){return category;}
    public Long getVendorId(){return vendorId;}
    public String getSettlementMonth(){return settlementMonth;}
    public boolean isReconciled(){return reconciled;}
    public void classify(String category){this.category=category;}
    public void link(Long vendorId,String settlementMonth){this.vendorId=vendorId;this.settlementMonth=settlementMonth;this.reconciled=false;}
    public void markReconciled(){this.reconciled=true;}
}
