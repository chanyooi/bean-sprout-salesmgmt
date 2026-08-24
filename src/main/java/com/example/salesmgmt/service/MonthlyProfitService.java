package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.BeanUsageCostResult;
import com.example.salesmgmt.domain.ExpenseType;
import com.example.salesmgmt.domain.MonthlyProfitReport;
import com.example.salesmgmt.domain.MonthlySalesReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MonthlyProfitService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final MonthlySalesReportService monthlySalesReportService;
    private final BeanInventoryService beanInventoryService;
    private final MonthlyExpenseService monthlyExpenseService;
    private final MonthlyCustomExpenseService monthlyCustomExpenseService;

    public MonthlyProfitService(
            MonthlySalesReportService monthlySalesReportService,
            BeanInventoryService beanInventoryService,
            MonthlyExpenseService monthlyExpenseService,
            MonthlyCustomExpenseService monthlyCustomExpenseService
    ) {
        this.monthlySalesReportService = monthlySalesReportService;
        this.beanInventoryService = beanInventoryService;
        this.monthlyExpenseService = monthlyExpenseService;
        this.monthlyCustomExpenseService = monthlyCustomExpenseService;
    }

    @Transactional(readOnly = true)
    public MonthlyProfitReport createReport(YearMonth month) {
        return createReport(month, monthlySalesReportService.createReport(month));
    }

    @Transactional(readOnly = true)
    public MonthlyProfitReport createReport(
            YearMonth month,
            MonthlySalesReport salesReport
    ) {
        BeanUsageCostResult beanCost = beanInventoryService.calculateUsageCost(month);
        Map<ExpenseType, BigDecimal> expenses = monthlyExpenseService.getExpenses(month);
        List<MonthlyCustomExpenseService.CustomExpenseRow> customExpenses =
                monthlyCustomExpenseService.getExpenses(month);

        List<MonthlyProfitReport.ExpenseRow> expenseRows = new ArrayList<>();
        BigDecimal otherExpenseTotal = BigDecimal.ZERO;

        for (ExpenseType type : ExpenseType.values()) {
            BigDecimal amount = safe(expenses.get(type));
            otherExpenseTotal = otherExpenseTotal.add(amount);
            expenseRows.add(new MonthlyProfitReport.ExpenseRow(
                    type.getLabel(),
                    money(amount)
            ));
        }

        for (MonthlyCustomExpenseService.CustomExpenseRow customExpense : customExpenses) {
            BigDecimal amount = safe(customExpense.amount());
            otherExpenseTotal = otherExpenseTotal.add(amount);
            expenseRows.add(new MonthlyProfitReport.ExpenseRow(
                    customExpense.name(),
                    money(amount)
            ));
        }

        BigDecimal sales = safe(salesReport.confirmedSales());
        BigDecimal beanUsageCost = safe(beanCost.knownUsageCost());
        BigDecimal totalCost = beanUsageCost.add(otherExpenseTotal);
        BigDecimal estimatedProfit = sales.subtract(totalCost);
        BigDecimal profitMargin = sales.signum() == 0
                ? BigDecimal.ZERO
                : estimatedProfit
                        .multiply(HUNDRED)
                        .divide(sales, 2, RoundingMode.HALF_UP);

        List<MonthlyProfitReport.VendorProfitRow> vendorRows = salesReport.vendorRows()
                .stream()
                .map(row -> createVendorRow(row, sales, totalCost))
                .toList();

        return new MonthlyProfitReport(
                month,
                money(sales),
                salesReport.missingPriceCount(),
                money(beanUsageCost),
                money(otherExpenseTotal),
                money(totalCost),
                money(estimatedProfit),
                profitMargin,
                beanCost.missingCostUsageCount(),
                List.copyOf(expenseRows),
                beanCost.rows(),
                vendorRows
        );
    }

    private MonthlyProfitReport.VendorProfitRow createVendorRow(
            MonthlySalesReport.VendorRow vendor,
            BigDecimal totalSales,
            BigDecimal totalCost
    ) {
        BigDecimal vendorSales = safe(vendor.confirmedSales());
        BigDecimal share = totalSales.signum() == 0
                ? BigDecimal.ZERO
                : vendorSales.divide(totalSales, 8, RoundingMode.HALF_UP);
        BigDecimal allocatedCost = totalCost
                .multiply(share)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal allocatedProfit = vendorSales.subtract(allocatedCost);

        return new MonthlyProfitReport.VendorProfitRow(
                vendor.vendorName(),
                money(vendorSales),
                share.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP),
                money(allocatedCost),
                money(allocatedProfit)
        );
    }

    private static BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
