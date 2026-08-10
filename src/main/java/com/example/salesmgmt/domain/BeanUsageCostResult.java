package com.example.salesmgmt.domain;

import java.math.BigDecimal;
import java.util.List;

public record BeanUsageCostResult(
        BigDecimal totalUsageBags,
        BigDecimal totalUsageKg,
        BigDecimal knownUsageCost,
        long missingCostUsageCount,
        List<Row> rows
) {
    public record Row(
            BeanType beanType,
            BeanOrigin origin,
            BigDecimal usedBags,
            BigDecimal usedKg,
            BigDecimal estimatedCost,
            BigDecimal effectiveAveragePricePerBag,
            long missingCostUsageCount
    ) {
        public String beanTypeLabel() {
            return beanType.getLabel();
        }

        public String originLabel() {
            return origin.getLabel();
        }
    }
}
