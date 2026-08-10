package com.example.salesmgmt.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SavedSaleRow(
        String orderNumber,
        LocalDate deliveryDate,
        String inputVendor,
        String statementVendor,
        String item,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount,
        BigDecimal returnContainerUnitPrice,
        String deliveryMethod,
        String note
) {
}
