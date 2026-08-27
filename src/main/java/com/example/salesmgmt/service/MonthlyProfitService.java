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
    private final SpecialItemAccountingService specialItemAccountingService;

    public MonthlyProfitService(
            MonthlySalesReportService monthlySalesReportService,
            BeanInventoryService beanInventoryService,
            MonthlyExpenseService monthlyExpenseService,
            SpecialItemAccountingService specialItemAccountingService
    ) {
        this.monthlySalesReportService = monthlySalesReportService;
        this.beanInventoryService = beanInventoryService;
        this.monthlyExpenseService = monthlyExpenseService;
        this.specialItemAccountingService = specialItemAccountingService;
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
        SpecialItemAccountingService.SpecialItemAccountingReport specialItems =
                specialItemAccountingService.report(month);

        BigDecimal tofuPurchaseCost = safe(specialItems.tofuPurchaseCost());

        List<MonthlyProfitReport.ExpenseRow> expenseRows = new ArrayList<>();
        BigDecimal otherExpenseTotal = BigDecimal.ZERO;
        for (ExpenseType type : ExpenseType.values()) {
            BigDecimal amount = type == ExpenseType.TOFU_PURCHASE
                    ? tofuPurchaseCost
                    : safe(expenses.get(type));

            otherExpenseTotal = otherExpenseTotal.add(amount);
            expenseRows.add(new MonthlyProfitReport.ExpenseRow(type, money(amount)));
        }

        /*
         * 손두부/두부판/회수통까지 반영된 실제 표시 매출을 기준으로
         * 이익과 이익률을 한 번만 계산한다.
         *
         * 이전에는 서버 계산 후 자바스크립트가 예상이익만 추가 보정해
         * 월매출·원가·예상이익·이익률의 기준이 서로 달라질 수 있었다.
         */
        BigDecimal sales = safe(specialItems.adjustedSales());
        BigDecimal beanUsageCost = safe(beanCost.knownUsageCost());
        BigDecimal totalCost = beanUsageCost.add(otherExpenseTotal);
        BigDecimal estimatedProfit = sales.subtract(totalCost);
        BigDecimal profitMargin = sales.signum() == 0
                ? BigDecimal.ZERO
                : estimatedProfit
                        .multiply(HUNDRED)
                        .divide(sales, 2, RoundingMode.HALF_UP);

        /*
         * 거래처별 표는 기존 판매자료의 매출 비중을 이용한 참고 배부값이다.
         * 특수품목 보정 전 거래처 합계와의 일관성을 위해 배부 비중 계산에는
         * 기존 판매보고서 매출을 사용하고, 배부할 총원가만 최신 원가를 반영한다.
         */
        BigDecimal allocationSales = safe(salesReport.confirmedSales());
        List<MonthlyProfitReport.VendorProfitRow> vendorRows = salesReport.vendorRows()
                .stream()
                .map(row -> createVendorRow(row, allocationSales, totalCost))
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
