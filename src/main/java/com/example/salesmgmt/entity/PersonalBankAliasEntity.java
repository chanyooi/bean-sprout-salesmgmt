package com.example.salesmgmt.entity;
import jakarta.persistence.*;
@Entity @Table(name="personal_bank_aliases",uniqueConstraints=@UniqueConstraint(columnNames="alias"))
public class PersonalBankAliasEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=255) private String alias;
    @Column(nullable=false) private Long vendorId;
    protected PersonalBankAliasEntity(){}
    public PersonalBankAliasEntity(String alias,Long vendorId){this.alias=alias;this.vendorId=vendorId;}
    public String getAlias(){return alias;}
    public Long getVendorId(){return vendorId;}
    public void changeVendor(Long id){this.vendorId=id;}
}
