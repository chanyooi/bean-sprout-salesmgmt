package com.example.salesmgmt.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DeliveryRecord(
        String orderNumber,
        LocalDate deliveryDate,
        String inputVendor,
        String statementVendor,
        String item,
        BigDecimal quantity,
        BigDecimal returnContainerUnitPrice,
        String deliveryMethod,
        String note,
        String sourceSheet,
        int sourceRow
) {
}
