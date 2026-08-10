package com.example.salesmgmt.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BeanUsageRow(
        Long id,
        LocalDate usageDate,
        BeanType beanType,
        BeanOrigin origin,
        BigDecimal bagCount,
        BigDecimal totalKg,
        String note
) {
    public String beanTypeLabel() {
        return beanType.getLabel();
    }

    public String originLabel() {
        return origin.getLabel();
    }
}
