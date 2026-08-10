package com.example.salesmgmt.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PromotionViewRow(
        Long id,
        Long vendorId,
        String vendorName,
        String itemName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal promotionUnitPrice,
        String memo,
        LocalDateTime createdAt
) {
    public boolean hasPrice() {
        return promotionUnitPrice != null;
    }
}
