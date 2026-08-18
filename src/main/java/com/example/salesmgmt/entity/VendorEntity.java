package com.example.salesmgmt.entity;

import com.example.salesmgmt.domain.StatementDeliveryMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "vendors",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vendors_input_name",
                columnNames = "input_name"
        )
)
public class VendorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "input_name", nullable = false, length = 100)
    private String inputName;

    @Column(name = "statement_name", nullable = false, length = 100)
    private String statementName;

    @Column(name = "statement_template_available", nullable = false)
    private boolean statementTemplateAvailable;

    @Enumerated(EnumType.STRING)
    @Column(name = "statement_delivery_method", length = 20)
    private StatementDeliveryMethod statementDeliveryMethod = StatementDeliveryMethod.UNASSIGNED;

    protected VendorEntity() {
    }

    public VendorEntity(
            String inputName,
            String statementName,
            boolean statementTemplateAvailable
    ) {
        this.inputName = inputName;
        this.statementName = statementName;
        this.statementTemplateAvailable = statementTemplateAvailable;
        this.statementDeliveryMethod = StatementDeliveryMethod.UNASSIGNED;
    }

    public Long getId() {
        return id;
    }

    public String getInputName() {
        return inputName;
    }

    public String getStatementName() {
        return statementName;
    }

    public boolean isStatementTemplateAvailable() {
        return statementTemplateAvailable;
    }

    public StatementDeliveryMethod getStatementDeliveryMethod() {
        return statementDeliveryMethod == null
                ? StatementDeliveryMethod.UNASSIGNED
                : statementDeliveryMethod;
    }

    public void updateStatementSettings(
            String statementName,
            boolean statementTemplateAvailable
    ) {
        this.statementName = statementName;
        this.statementTemplateAvailable = statementTemplateAvailable;
    }

    public void updateStatementDeliveryMethod(
            StatementDeliveryMethod statementDeliveryMethod
    ) {
        this.statementDeliveryMethod = statementDeliveryMethod == null
                ? StatementDeliveryMethod.UNASSIGNED
                : statementDeliveryMethod;
    }
}
