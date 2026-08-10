package com.example.salesmgmt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    public void updateStatementSettings(
            String statementName,
            boolean statementTemplateAvailable
    ) {
        this.statementName = statementName;
        this.statementTemplateAvailable = statementTemplateAvailable;
    }
}
