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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(
        name = "bean_stock_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bean_stock_setting_type_origin",
                columnNames = {"bean_type", "origin"}
        )
)
public class BeanStockSettingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "bean_type", nullable = false, length = 30)
    private BeanType beanType;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 30)
    private BeanOrigin origin;

    @Column(name = "low_stock_threshold_bags", nullable = false, precision = 12, scale = 3)
    private BigDecimal lowStockThresholdBags;

    protected BeanStockSettingEntity() {
    }

    public BeanStockSettingEntity(
            BeanType beanType,
            BeanOrigin origin,
            BigDecimal lowStockThresholdBags
    ) {
        this.beanType = beanType;
        this.origin = origin;
        updateThreshold(lowStockThresholdBags);
    }

    public Long getId() {
        return id;
    }

    public BeanType getBeanType() {
        return beanType;
    }

    public BeanOrigin getOrigin() {
        return origin;
    }

    public BigDecimal getLowStockThresholdBags() {
        return lowStockThresholdBags;
    }

    public void updateThreshold(BigDecimal threshold) {
        if (threshold == null || threshold.signum() < 0) {
            throw new IllegalArgumentException("재고 부족 기준은 0포대 이상이어야 합니다.");
        }
        this.lowStockThresholdBags = threshold.setScale(3, RoundingMode.HALF_UP);
    }
}
