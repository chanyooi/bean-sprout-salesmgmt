package com.example.salesmgmt.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_promotions")
public class SalesPromotionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorEntity vendor;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "promotion_unit_price", precision = 14, scale = 2)
    private BigDecimal promotionUnitPrice;

    @Column(name = "memo", length = 1000)
    private String memo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected SalesPromotionEntity() {}

    public SalesPromotionEntity(
            VendorEntity vendor,
            String itemName,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal promotionUnitPrice,
            String memo
    ) {
        this.vendor = vendor;
        this.itemName = normalizeItem(itemName);
        this.startDate = startDate;
        this.endDate = endDate;
        this.promotionUnitPrice = promotionUnitPrice;
        this.memo = blankToNull(memo);
        this.createdAt = LocalDateTime.now();
        validate();
    }

    public Long getId() { return id; }
    public VendorEntity getVendor() { return vendor; }
    public String getItemName() { return itemName; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public BigDecimal getPromotionUnitPrice() { return promotionUnitPrice; }
    public String getMemo() { return memo; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void updatePriceAndMemo(
            BigDecimal promotionUnitPrice,
            String memo
    ) {
        this.promotionUnitPrice = promotionUnitPrice;
        this.memo = blankToNull(memo);
        validate();
    }

    private void validate() {
        if (vendor == null) {
            throw new IllegalArgumentException("거래처가 필요합니다.");
        }
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("행사 품목이 필요합니다.");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("행사 시작일과 종료일이 필요합니다.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("행사 종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (promotionUnitPrice != null
                && promotionUnitPrice.signum() < 0) {
            throw new IllegalArgumentException("행사 단가는 0원 이상이어야 합니다.");
        }
    }

    private static String normalizeItem(String value) {
        return value == null ? null : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
