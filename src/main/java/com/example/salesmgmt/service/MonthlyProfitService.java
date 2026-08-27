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

@Service
public class MonthlyProfitService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final MonthlySalesReportService monthlySalesReportService;
    private final BeanInventoryService beanInventoryService;
    private final MonthlyExpenseItemService monthlyExpenseItemService;
    private final SpecialItemAccountingService specialItemAccountingService;
    private final VendorProfitAnalysisService vendorProfitAnalysisService;

    public MonthlyProfitService(
            MonthlySalesReportService monthlySalesReportService,
            BeanInventoryService beanInventoryService,
            MonthlyExpenseItemService monthlyExpenseItemService,
            SpecialItemAccountingService specialItemAccountingService,
            VendorProfitAnalysisService vendorProfitAnalysisService
    ) {
        this.monthlySalesReportService = monthlySalesReportService;
        this.beanInventoryService = beanInventoryService;
        this.monthlyExpenseItemService = monthlyExpenseItemService;
        this.specialItemAccountingService = specialItemAccountingService;
        this.vendorProfitAnalysisService = vendorProfitAnalysisService;
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
        SpecialItemAccountingService.SpecialItemAccountingReport specialItems =
                specialItemAccountingService.report(month);

        BigDecimal tofuPurchaseCost = safe(specialItems.tofuPurchaseCost());
        BigDecimal operatingExpenseTotal = safe(monthlyExpenseItemService.getOperatingExpenseTotal(month));
        BigDecimal otherExpenseTotal = operatingExpenseTotal.add(tofuPurchaseCost);

        List<MonthlyProfitReport.ExpenseRow> expenseRows = new ArrayList<>();
        expenseRows.add(new MonthlyProfitReport.ExpenseRow(ExpenseType.OTHER, money(operatingExpenseTotal)));
        expenseRows.add(new MonthlyProfitReport.ExpenseRow(ExpenseType.TOFU_PURCHASE, money(tofuPurchaseCost)));

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
         * 거래처별 이익은 비용 성격에 따라 서로 다른 배부 기준을 사용한다.
         * - 콩·박스·비닐·손두부: 실제 품목/수량 직접원가
         * - 인건비·시설비: 판매중량 비중
         * - 차량·배송비: 거래처 배송일수 비중
         * - 기타 포장비: 포장 개수 비중
         * - 복리후생·기타: 특정 거래처에 억지로 배부하지 않고 회사 공통비로 별도 표시
         */
        VendorProfitAnalysisService.AnalysisResult vendorAnalysis =
                vendorProfitAnalysisService.analyze(month, beanCost);

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
                money(vendorAnalysis.unallocatedCompanyExpense()),
                vendorAnalysis.rows()
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
