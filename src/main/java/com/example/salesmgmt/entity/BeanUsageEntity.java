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
        name = "bean_usages",
        indexes = @Index(
                name = "idx_bean_usages_date_type_origin",
                columnList = "usage_date,bean_type,origin"
        )
)
public class BeanUsageEntity {

    public static final BigDecimal KG_PER_BAG = new BigDecimal("25");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "bean_type", nullable = false, length = 30)
    private BeanType beanType;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 30)
    private BeanOrigin origin;

    @Column(name = "bag_count", nullable = false, precision = 12, scale = 3)
    private BigDecimal bagCount;

    @Column(name = "unit_price_per_kg", precision = 18, scale = 2)
    private BigDecimal unitPricePerKg;

    @Column(name = "note", length = 500)
    private String note;

    protected BeanUsageEntity() {
    }

    public BeanUsageEntity(
            LocalDate usageDate,
            BeanType beanType,
            BeanOrigin origin,
            BigDecimal bagCount,
            String note
    ) {
        this(usageDate, beanType, origin, bagCount, null, note);
    }

    public BeanUsageEntity(
            LocalDate usageDate,
            BeanType beanType,
            BeanOrigin origin,
            BigDecimal bagCount,
            BigDecimal unitPricePerKg,
            String note
    ) {
        if (usageDate == null) {
            throw new IllegalArgumentException("사용 날짜를 입력해주세요.");
        }
        if (beanType == null || origin == null) {
            throw new IllegalArgumentException("콩 종류와 원산지를 선택해주세요.");
        }
        if (bagCount == null || bagCount.signum() <= 0) {
            throw new IllegalArgumentException("사용 포대 수는 0보다 커야 합니다.");
        }
        if (unitPricePerKg != null && unitPricePerKg.signum() <= 0) {
            throw new IllegalArgumentException("kg당 단가는 0원보다 크게 입력하거나 비워주세요.");
        }

        this.usageDate = usageDate;
        this.beanType = beanType;
        this.origin = origin;
        this.bagCount = bagCount.setScale(3, RoundingMode.HALF_UP);
        this.unitPricePerKg = unitPricePerKg == null
                ? null
                : unitPricePerKg.setScale(2, RoundingMode.HALF_UP);
        this.note = blankToNull(note);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getUsageDate() {
        return usageDate;
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

    public BigDecimal getUnitPricePerKg() {
        return unitPricePerKg;
    }

    public String getNote() {
        return note;
    }

    public BigDecimal getTotalKg() {
        return bagCount.multiply(KG_PER_BAG).setScale(3, RoundingMode.HALF_UP);
    }

    public BigDecimal getExplicitUsageCost() {
        if (unitPricePerKg == null) {
            return null;
        }
        return getTotalKg()
                .multiply(unitPricePerKg)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
