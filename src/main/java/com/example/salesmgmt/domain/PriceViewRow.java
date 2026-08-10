package com.example.salesmgmt.domain;

import java.math.BigDecimal;

public record PriceViewRow(
        Long id,
        String itemName,
        BigDecimal unitPrice,
        String sourceSheet
) {
}
