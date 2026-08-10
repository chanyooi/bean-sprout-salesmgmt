package com.example.salesmgmt.entity;

import com.example.salesmgmt.domain.BeanOrigin;
import com.example.salesmgmt.domain.BeanType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(
        name = "bean_purchases",
        indexes = @Index(
                name = "idx_bean_purchases_date_type_origin",
                columnList = "purchase_date,bean_type,origin"
        )
)
public class BeanPurchaseEntity {

    public static final BigDecimal KG_PER_BAG = new BigDecimal("25");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "bean_type", nullable = false, length = 30)
    private BeanType beanType;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 30)
    private BeanOrigin origin;

    @Column(name = "bag_count", nullable = false, precision = 12, scale = 3)
    private BigDecimal bagCount;

    @Column(name = "unit_price_per_bag", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPricePerBag;

    @Column(name = "note", length = 500)
    private String note;

    protected BeanPurchaseEntity() {
    }

    public BeanPurchaseEntity(
            LocalDate purchaseDate,
            BeanType beanType,
            BeanOrigin origin,
            BigDecimal bagCount,
            BigDecimal unitPricePerBag,
            String note
    ) {
        if (purchaseDate == null) {
            throw new IllegalArgumentException("매입 날짜를 입력해주세요.");
        }
        if (bagCount == null || bagCount.signum() <= 0) {
            throw new IllegalArgumentException("구입 포대 수는 0보다 커야 합니다.");
        }
        if (unitPricePerBag == null || unitPricePerBag.signum() < 0) {
            throw new IllegalArgumentException("포대당 매입단가는 0원 이상이어야 합니다.");
        }

        this.purchaseDate = purchaseDate;
        this.beanType = beanType;
        this.origin = origin;
        this.bagCount = bagCount.setScale(3, RoundingMode.HALF_UP);
        this.unitPricePerBag = unitPricePerBag.setScale(2, RoundingMode.HALF_UP);
        this.note = blankToNull(note);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public BeanType getBeanType() {
        return beanType;
    }

    public BeanOrigin getOrigin() {
        return origin;
    }

    public BigDecimal getBagCount() {
        return bagCount;
    }

    public BigDecimal getUnitPricePerBag() {
        return unitPricePerBag;
    }

    public String getNote() {
        return note;
    }

    public BigDecimal getTotalKg() {
        return bagCount.multiply(KG_PER_BAG).setScale(3, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalAmount() {
        return bagCount.multiply(unitPricePerBag).setScale(2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
