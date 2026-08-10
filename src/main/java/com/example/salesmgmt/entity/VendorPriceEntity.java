package com.example.salesmgmt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(
        name = "vendor_prices",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vendor_prices_vendor_item",
                columnNames = {"vendor_id", "item_name"}
        )
)
public class VendorPriceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorEntity vendor;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "source_sheet", length = 100)
    private String sourceSheet;

    protected VendorPriceEntity() {
    }

    public VendorPriceEntity(
            VendorEntity vendor,
            String itemName,
            BigDecimal unitPrice,
            String sourceSheet
    ) {
        this.vendor = vendor;
        this.itemName = itemName;
        this.unitPrice = unitPrice;
        this.sourceSheet = blankToNull(sourceSheet);
    }

    public Long getId() {
        return id;
    }

    public VendorEntity getVendor() {
        return vendor;
    }

    public String getItemName() {
        return itemName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public String getSourceSheet() {
        return sourceSheet;
    }

    public void update(BigDecimal unitPrice, String sourceSheet) {
        this.unitPrice = unitPrice;
        this.sourceSheet = blankToNull(sourceSheet);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
