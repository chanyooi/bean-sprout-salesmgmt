package com.example.salesmgmt.domain;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record MonthlyProfitReport(
        YearMonth month,
        BigDecimal confirmedSales,
        long missingSalesPriceCount,
        BigDecimal beanUsageCost,
        BigDecimal otherExpenseTotal,
        BigDecimal totalCost,
        BigDecimal estimatedProfit,
        BigDecimal estimatedProfitMarginPercent,
        long missingBeanCostUsageCount,
        List<ExpenseRow> expenseRows,
        List<BeanUsageCostResult.Row> beanCostRows,
        List<VendorProfitRow> vendorRows
) {
    public record ExpenseRow(
            ExpenseType expenseType,
            BigDecimal amount
    ) {
        public String label() {
            return expenseType.getLabel();
        }
    }

    public record VendorProfitRow(
            String vendorName,
            BigDecimal sales,
            BigDecimal salesSharePercent,
            BigDecimal allocatedCost,
            BigDecimal allocatedEstimatedProfit
    ) {
    }
}
