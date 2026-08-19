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
import java.math.RoundingMode;

@Entity
@Table(
        name = "sales_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sales_items_order_item",
                columnNames = {"sales_order_id", "item_name"}
        )
)
public class SalesItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrderEntity salesOrder;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_amount", precision = 18, scale = 2)
    private BigDecimal lineAmount;

    @Column(name = "manual_price_override", nullable = false)
    private boolean manualPriceOverride = false;

    protected SalesItemEntity() {
    }

    public SalesItemEntity(
            SalesOrderEntity salesOrder,
            String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice
    ) {
        this.salesOrder = salesOrder;
        this.itemName = itemName;
        validateQuantity(quantity);
        validateUnitPrice(unitPrice);
        this.quantity = normalizeQuantity(quantity);
        applyUnitPrice(unitPrice);
        this.manualPriceOverride = false;
    }

    public Long getId() {
        return id;
    }

    public SalesOrderEntity getSalesOrder() {
        return salesOrder;
    }

    public String getItemName() {
        return itemName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineAmount() {
        return lineAmount;
    }

    public boolean isManualPriceOverride() {
        return manualPriceOverride;
    }

    /** 재업로드 시 수량은 갱신하되 사용자가 직접 바꾼 판매단가는 보존합니다. */
    public boolean updateFromUpload(
            BigDecimal newQuantity,
            BigDecimal resolvedUnitPrice,
            boolean replaceUnitPrice
    ) {
        validateQuantity(newQuantity);

        BigDecimal targetUnitPrice = this.unitPrice;

        if (!manualPriceOverride) {
            if (replaceUnitPrice && resolvedUnitPrice != null) {
                targetUnitPrice = resolvedUnitPrice;
            } else if (targetUnitPrice == null && resolvedUnitPrice != null) {
                targetUnitPrice = resolvedUnitPrice;
            }
        }

        boolean quantityChanged = this.quantity.compareTo(newQuantity) != 0;
        boolean priceChanged = !sameNumber(this.unitPrice, targetUnitPrice);

        if (!quantityChanged && !priceChanged) {
            return false;
        }

        this.quantity = normalizeQuantity(newQuantity);
        applyUnitPrice(targetUnitPrice);
        return true;
    }

    /** 거래처 상세에서 특정 주문의 단가를 수정하면 해당 주문만 수동단가로 고정합니다. */
    public void updateManually(
            BigDecimal newQuantity,
            BigDecimal newUnitPrice
    ) {
        validateQuantity(newQuantity);
        validateUnitPrice(newUnitPrice);

        this.quantity = normalizeQuantity(newQuantity);
        applyUnitPrice(newUnitPrice);
        this.manualPriceOverride = true;
    }

    /** 기본단가 변경 시 수동수정되지 않은 주문에만 새 기본단가를 반영합니다. */
    public void applyBaseUnitPrice(BigDecimal unitPrice) {
        if (manualPriceOverride) {
            return;
        }
        applyUnitPrice(unitPrice);
    }

    private void validateQuantity(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("판매 수량은 0보다 커야 합니다.");
        }
    }

    private void validateUnitPrice(BigDecimal value) {
        if (value == null || value.signum() >= 0) {
            return;
        }

        if (!"회수통".equals(itemName)) {
            throw new IllegalArgumentException(
                    "회수통을 제외한 판매단가는 0원 이상이어야 합니다."
            );
        }
    }

    private BigDecimal normalizeQuantity(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private boolean sameNumber(BigDecimal first, BigDecimal second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.compareTo(second) == 0;
    }

    public void applyUnitPrice(BigDecimal unitPrice) {
        validateUnitPrice(unitPrice);

        if (unitPrice == null) {
            this.unitPrice = null;
            this.lineAmount = null;
            return;
        }

        this.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        this.lineAmount = quantity
                .multiply(this.unitPrice)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
