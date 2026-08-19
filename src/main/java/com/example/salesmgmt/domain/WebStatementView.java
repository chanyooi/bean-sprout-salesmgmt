package com.example.salesmgmt.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record WebStatementView(
        YearMonth month,
        Long vendorId,
        String vendorName,
        String deliveryLabel,
        BigDecimal grossAmount,
        BigDecimal returnContainerQuantity,
        BigDecimal returnContainerAmount,
        BigDecimal totalAmount,
        long missingPriceCount,
        List<String> itemNames,
        List<BigDecimal> itemQuantityTotals,
        List<ItemSummary> itemSummaries,
        List<DailyRow> dailyRows
) {
    public record ItemSummary(
            String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount
    ) {}

    public record DailyRow(
            LocalDate date,
            List<BigDecimal> quantities,
            BigDecimal amount
    ) {}
}
