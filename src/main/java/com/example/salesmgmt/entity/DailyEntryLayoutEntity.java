package com.example.salesmgmt.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "daily_entry_layout")
public class DailyEntryLayoutEntity {
    @Id
    private Long id = 1L;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String vendorOrder = "";
    @Column(columnDefinition = "TEXT", nullable = false)
    private String hiddenVendors = "";
    @Column(columnDefinition = "TEXT", nullable = false)
    private String productOrder = "";
    @Column(columnDefinition = "TEXT", nullable = false)
    private String hiddenProducts = "";
    public String getVendorOrder() { return vendorOrder; }
    public String getHiddenVendors() { return hiddenVendors; }
    public String getProductOrder() { return productOrder; }
    public String getHiddenProducts() { return hiddenProducts; }
    public void update(String vendors, String hiddenVendors, String products, String hiddenProducts) {
        this.vendorOrder = vendors;
        this.hiddenVendors = hiddenVendors;
        this.productOrder = products;
        this.hiddenProducts = hiddenProducts;
    }
}
