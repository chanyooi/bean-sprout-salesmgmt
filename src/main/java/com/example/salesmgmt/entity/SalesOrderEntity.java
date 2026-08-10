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
import java.time.LocalDate;

@Entity
@Table(
        name = "sales_orders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sales_orders_order_number",
                columnNames = "order_number"
        )
)
public class SalesOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, length = 40)
    private String orderNumber;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorEntity vendor;

    @Column(name = "return_container_unit_price", precision = 14, scale = 2)
    private BigDecimal returnContainerUnitPrice;

    @Column(name = "delivery_method", length = 100)
    private String deliveryMethod;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "source_sheet", length = 40)
    private String sourceSheet;

    @Column(name = "source_row")
    private Integer sourceRow;

    protected SalesOrderEntity() {
    }

    public SalesOrderEntity(
            String orderNumber,
            LocalDate deliveryDate,
            VendorEntity vendor,
            BigDecimal returnContainerUnitPrice,
            String deliveryMethod,
            String note,
            String sourceSheet,
            Integer sourceRow
    ) {
        this.orderNumber = orderNumber;
        this.deliveryDate = deliveryDate;
        this.vendor = vendor;
        this.returnContainerUnitPrice = returnContainerUnitPrice;
        this.deliveryMethod = blankToNull(deliveryMethod);
        this.note = blankToNull(note);
        this.sourceSheet = blankToNull(sourceSheet);
        this.sourceRow = sourceRow;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public VendorEntity getVendor() {
        return vendor;
    }

    public BigDecimal getReturnContainerUnitPrice() {
        return returnContainerUnitPrice;
    }

    public String getDeliveryMethod() {
        return deliveryMethod;
    }

    public String getNote() {
        return note;
    }

    public String getSourceSheet() {
        return sourceSheet;
    }

    public Integer getSourceRow() {
        return sourceRow;
    }

    public void fillMissingMetadata(
            BigDecimal returnContainerUnitPrice,
            String deliveryMethod,
            String note
    ) {
        if (this.returnContainerUnitPrice == null) {
            this.returnContainerUnitPrice = returnContainerUnitPrice;
        }
        if (this.deliveryMethod == null) {
            this.deliveryMethod = blankToNull(deliveryMethod);
        }
        if (this.note == null) {
            this.note = blankToNull(note);
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
