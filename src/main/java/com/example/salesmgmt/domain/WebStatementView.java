package com.example.salesmgmt.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record WebStatementView(
        YearMonth month,
        Long vendorId,
        String vendorName,
        BigDecimal totalAmount,
        long missingPriceCount,
        List<String> itemNames,
        List<DailyRow> dailyRows
) {
    public record DailyRow(
            LocalDate date,
            List<BigDecimal> quantities,
            BigDecimal amount
    ) {}
}
