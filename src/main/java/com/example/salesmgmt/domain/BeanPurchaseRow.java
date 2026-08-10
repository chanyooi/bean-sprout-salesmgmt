package com.example.salesmgmt.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BeanPurchaseRow(
        Long id,
        LocalDate purchaseDate,
        BeanType beanType,
        BeanOrigin origin,
        BigDecimal bagCount,
        BigDecimal totalKg,
        BigDecimal unitPricePerBag,
        BigDecimal totalAmount,
        String note
) {
    public String beanTypeLabel() {
        return beanType.getLabel();
    }

    public String originLabel() {
        return origin.getLabel();
    }
}
