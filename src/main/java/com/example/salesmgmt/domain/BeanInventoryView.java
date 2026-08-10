package com.example.salesmgmt.domain;

import java.time.LocalDate;
import java.util.List;

public record BeanInventoryView(
        LocalDate asOfDate,
        List<BeanStockSummaryRow> stockRows,
        List<BeanPurchaseRow> recentPurchases,
        List<BeanUsageRow> recentUsages,
        long lowStockCount
) {
}
