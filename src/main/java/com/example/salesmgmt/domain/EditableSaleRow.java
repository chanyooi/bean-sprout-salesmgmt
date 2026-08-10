package com.example.salesmgmt.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EditableSaleRow(
        Long itemId,
        Long orderId,
        String orderNumber,
        LocalDate deliveryDate,
        Long vendorId,
        String inputVendor,
        String statementVendor,
        String item,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount,
        String deliveryMethod,
        String note
) {
}
