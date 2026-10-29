package com.example.salesmgmt.entity;

import jakarta.persistence.*;

@Entity
@Table(name="personal_bank_category_rules",uniqueConstraints=@UniqueConstraint(columnNames="counterparty"))
public class PersonalBankCategoryRuleEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false,length=255) private String counterparty;
    @Column(nullable=false,length=80) private String category;
    protected PersonalBankCategoryRuleEntity(){}
    public PersonalBankCategoryRuleEntity(String counterparty,String category) {
        this.counterparty=counterparty;this.category=category;
    }
    public String getCategory(){return category;}
    public void updateCategory(String category){this.category=category;}
}
