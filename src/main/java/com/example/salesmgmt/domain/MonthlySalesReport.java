package com.example.salesmgmt.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record MonthlySalesReport(
        YearMonth month,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal confirmedSales,
        BigDecimal totalQuantity,
        long orderCount,
        long vendorCount,
        long itemRecordCount,
        long missingPriceCount,
        List<VendorRow> vendorRows,
        List<ItemRow> itemRows,
        List<DailyRow> dailyRows
) {
    public boolean isEmpty() {
        return itemRecordCount == 0;
    }

    public boolean hasMissingPrices() {
        return missingPriceCount > 0;
    }

    public record VendorRow(
            String vendorName,
            BigDecimal confirmedSales,
            BigDecimal totalQuantity,
            long orderCount,
            long itemRecordCount,
            long missingPriceCount
    ) {
    }

    public record ItemRow(
            String itemName,
            BigDecimal totalQuantity,
            BigDecimal confirmedSales,
            long vendorCount,
            long itemRecordCount,
            long missingPriceCount
    ) {
    }

    public record DailyRow(
            LocalDate deliveryDate,
            BigDecimal confirmedSales,
            BigDecimal totalQuantity,
            long orderCount,
            long vendorCount,
            long itemRecordCount,
            long missingPriceCount
    ) {
    }
}
