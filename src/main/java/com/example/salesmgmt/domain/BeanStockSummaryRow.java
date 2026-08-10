package com.example.salesmgmt.domain;

import java.math.BigDecimal;

public record BeanStockSummaryRow(
        BeanType beanType,
        BeanOrigin origin,
        BigDecimal purchasedBags,
        BigDecimal purchasedKg,
        BigDecimal usedBags,
        BigDecimal usedKg,
        BigDecimal currentBags,
        BigDecimal currentKg,
        BigDecimal weightedAveragePricePerBag,
        BigDecimal estimatedStockValue,
        BigDecimal lowStockThresholdBags,
        boolean active,
        boolean lowStock
) {
    public String beanTypeLabel() {
        return beanType.getLabel();
    }

    public String originLabel() {
        return origin.getLabel();
    }
}
