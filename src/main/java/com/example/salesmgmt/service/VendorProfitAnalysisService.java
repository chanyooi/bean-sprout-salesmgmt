package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.BeanType;
import com.example.salesmgmt.domain.BeanUsageCostResult;
import com.example.salesmgmt.domain.MonthlyProfitReport;
import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VendorProfitAnalysisService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal KG_PER_BAG = new BigDecimal("25");
    private static final BigDecimal RAW_BEAN_PRICE_PER_KG = new BigDecimal("3800");
    private static final BigDecimal BOX_COST_PER_UNIT = new BigDecimal("400");
    private static final BigDecimal MUNG_FALLBACK_BAGS_PER_MONTH = new BigDecimal("6");

    private final SalesItemRepository salesItemRepository;
    private final MonthlyExpenseItemService monthlyExpenseItemService;
    private final SpecialItemAccountingService specialItemAccountingService;

    public VendorProfitAnalysisService(
            SalesItemRepository salesItemRepository,
            MonthlyExpenseItemService monthlyExpenseItemService,
            SpecialItemAccountingService specialItemAccountingService
    ) {
        this.salesItemRepository = salesItemRepository;
        this.monthlyExpenseItemService = monthlyExpenseItemService;
        this.specialItemAccountingService = specialItemAccountingService;
    }

    @Transactional(readOnly = true)
    public List<MonthlyProfitReport.VendorProfitRow> createRows(
            YearMonth month,
            BeanUsageCostResult beanCost
    ) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        List<SalesItemEntity> items = salesItemRepository.findForMonthlyReport(start, end);

        Map<String, VendorAccumulator> vendors = new LinkedHashMap<>();
        Map<BeanType, BigDecimal> totalFinishedKgByBean = emptyBeanMap();
        BigDecimal totalFinishedKg = ZERO;
        BigDecimal totalVinylUnits = ZERO;
        BigDecimal totalTofuResaleQty = ZERO;
        LocalDate latestProductionSalesDate = null;

        for (SalesItemEntity item : items) {
            String itemName = normalize(item.getItemName());
            String vendorName = item.getSalesOrder().getVendor().getInputName();
            String statementName = normalize(item.getSalesOrder().getVendor().getStatementName());
            BigDecimal quantity = nz(item.getQuantity());
            BigDecimal lineAmount = nz(item.getLineAmount());

            VendorAccumulator vendor = vendors.computeIfAbsent(
                    vendorName,
                    VendorAccumulator::new
            );

            if ("손두부".equals(itemName)) {
                if (statementName.contains("팔공")) {
                    // 팔공 손두부 행은 매입원가이므로 거래처 매출에서 제외한다.
                    continue;
                }
                vendor.sales = vendor.sales.add(lineAmount);
                vendor.tofuResaleQty = vendor.tofuResaleQty.add(quantity.abs());
                totalTofuResaleQty = totalTofuResaleQty.add(quantity.abs());
                continue;
            }

            if ("두부판".equals(itemName)) {
                // 기록 금액은 제외하고, 실제 판 반납수익은 아래에서 다시 배부한다.
                continue;
            }

            // 회수통 등 비생산 항목도 기존 회계 기준대로 매출에는 포함한다.
            vendor.sales = vendor.sales.add(lineAmount);

            ProductSpec spec = productSpec(itemName);
            if (spec == null) {
                continue;
            }

            BigDecimal finishedKg = quantity.multiply(spec.kgPerUnit());
            vendor.finishedKgByBean.merge(spec.beanType(), finishedKg, BigDecimal::add);
            vendor.finishedKg = vendor.finishedKg.add(finishedKg);
            totalFinishedKgByBean.merge(spec.beanType(), finishedKg, BigDecimal::add);
            totalFinishedKg = totalFinishedKg.add(finishedKg);

            if (spec.box()) {
                vendor.boxUnits = vendor.boxUnits.add(quantity);
            }
            if (spec.vinyl()) {
                vendor.vinylUnits = vendor.vinylUnits.add(quantity);
                totalVinylUnits = totalVinylUnits.add(quantity);
            }

            LocalDate deliveryDate = item.getSalesOrder().getDeliveryDate();
            if (deliveryDate != null
                    && (latestProductionSalesDate == null || deliveryDate.isAfter(latestProductionSalesDate))) {
                latestProductionSalesDate = deliveryDate;
            }
        }

        SpecialItemAccountingService.SpecialItemAccountingReport special =
                specialItemAccountingService.report(month);

        // 두부판 반납수익과 손두부 매입원가는 손두부를 실제 판매한 거래처에 수량 비례 배부한다.
        if (totalTofuResaleQty.signum() > 0) {
            for (VendorAccumulator vendor : vendors.values()) {
                if (vendor.tofuResaleQty.signum() <= 0) {
                    continue;
                }
                BigDecimal tofuShare = vendor.tofuResaleQty.divide(
                        totalTofuResaleQty,
                        10,
                        RoundingMode.HALF_UP
                );
                vendor.sales = vendor.sales.add(
                        nz(special.tofuTrayReturnRevenue()).multiply(tofuShare)
                );
                vendor.tofuPurchaseCost = nz(special.tofuPurchaseCost()).multiply(tofuShare);
            }
        }

        Map<BeanType, BigDecimal> usedRawKgByBean = rawBeanKg(month, beanCost, latestProductionSalesDate);
        Map<BeanType, BigDecimal> beanCostPerFinishedKg = emptyBeanMap();
        for (BeanType beanType : BeanType.values()) {
            BigDecimal finishedKg = totalFinishedKgByBean.getOrDefault(beanType, ZERO);
            BigDecimal rawKg = usedRawKgByBean.getOrDefault(beanType, ZERO);
            BigDecimal rawCost = rawKg.multiply(RAW_BEAN_PRICE_PER_KG);
            BigDecimal costPerFinishedKg = finishedKg.signum() == 0
                    ? ZERO
                    : rawCost.divide(finishedKg, 8, RoundingMode.HALF_UP);
            beanCostPerFinishedKg.put(beanType, costPerFinishedKg);
        }

        List<MonthlyExpenseItemService.ExpenseItemView> expenseItems =
                monthlyExpenseItemService.getItems(month);
        BigDecimal vinylExpense = expenseByName(expenseItems, "비닐");
        BigDecimal boxExpenseEntered = expenseByName(expenseItems, "박스");
        BigDecimal operatingExpenseTotal = expenseItems.stream()
                .map(MonthlyExpenseItemService.ExpenseItemView::amount)
                .reduce(ZERO, BigDecimal::add);

        // 박스는 실제 사용수량 x 400원으로 직접 계산하고, 비닐도 실제 포장 개수로 배부한다.
        // 따라서 입력된 박스/비닐 비용은 공통비에서 중복 차감하지 않는다.
        BigDecimal commonOverhead = operatingExpenseTotal
                .subtract(vinylExpense)
                .subtract(boxExpenseEntered);
        if (commonOverhead.signum() < 0) {
            commonOverhead = ZERO;
        }

        BigDecimal vinylCostPerUnit = totalVinylUnits.signum() == 0
                ? ZERO
                : vinylExpense.divide(totalVinylUnits, 8, RoundingMode.HALF_UP);

        List<MonthlyProfitReport.VendorProfitRow> rows = new ArrayList<>();
        for (VendorAccumulator vendor : vendors.values()) {
            if (vendor.sales.signum() == 0
                    && vendor.finishedKg.signum() == 0
                    && vendor.tofuPurchaseCost.signum() == 0) {
                continue;
            }

            BigDecimal beanDirectCost = ZERO;
            for (BeanType beanType : BeanType.values()) {
                beanDirectCost = beanDirectCost.add(
                        vendor.finishedKgByBean.getOrDefault(beanType, ZERO)
                                .multiply(beanCostPerFinishedKg.getOrDefault(beanType, ZERO))
                );
            }

            BigDecimal boxCost = vendor.boxUnits.multiply(BOX_COST_PER_UNIT);
            BigDecimal vinylCost = vendor.vinylUnits.multiply(vinylCostPerUnit);
            BigDecimal directCost = beanDirectCost
                    .add(boxCost)
                    .add(vinylCost)
                    .add(vendor.tofuPurchaseCost);

            BigDecimal overhead = totalFinishedKg.signum() == 0
                    ? ZERO
                    : commonOverhead.multiply(
                            vendor.finishedKg.divide(totalFinishedKg, 10, RoundingMode.HALF_UP)
                    );

            BigDecimal totalCost = directCost.add(overhead);
            BigDecimal profit = vendor.sales.subtract(totalCost);
            BigDecimal margin = vendor.sales.signum() == 0
                    ? ZERO
                    : profit.multiply(HUNDRED)
                            .divide(vendor.sales, 2, RoundingMode.HALF_UP);

            rows.add(new MonthlyProfitReport.VendorProfitRow(
                    vendor.vendorName,
                    money(vendor.sales),
                    money(vendor.finishedKg),
                    money(directCost),
                    money(overhead),
                    money(totalCost),
                    money(profit),
                    margin
            ));
        }

        return rows.stream()
                .sorted(Comparator
                        .comparing(MonthlyProfitReport.VendorProfitRow::sales)
                        .reversed()
                        .thenComparing(MonthlyProfitReport.VendorProfitRow::vendorName))
                .toList();
    }

    private Map<BeanType, BigDecimal> rawBeanKg(
            YearMonth month,
            BeanUsageCostResult beanCost,
            LocalDate latestProductionSalesDate
    ) {
        Map<BeanType, BigDecimal> result = emptyBeanMap();
        if (beanCost != null && beanCost.rows() != null) {
            for (BeanUsageCostResult.Row row : beanCost.rows()) {
                result.merge(row.beanType(), nz(row.usedKg()), BigDecimal::add);
            }
        }

        LocalDate through = latestProductionSalesDate == null
                ? month.atEndOfMonth()
                : latestProductionSalesDate;
        int productionDays = countProductionDays(month.atDay(1), through);

        // 실제 사용기록이 없는 과거/초기 데이터는 현재 공장 사용패턴으로 보완한다.
        putFallbackIfZero(result, BeanType.LARGE,
                KG_PER_BAG.multiply(BigDecimal.valueOf(productionDays)));
        putFallbackIfZero(result, BeanType.MEDIUM,
                KG_PER_BAG.multiply(BigDecimal.valueOf(productionDays * 2L)));
        putFallbackIfZero(result, BeanType.SMALL,
                KG_PER_BAG.multiply(BigDecimal.valueOf(productionDays * 3L)));
        putFallbackIfZero(result, BeanType.MUNG,
                KG_PER_BAG.multiply(MUNG_FALLBACK_BAGS_PER_MONTH));

        return result;
    }

    private int countProductionDays(LocalDate start, LocalDate end) {
        if (end == null || end.isBefore(start)) {
            return 0;
        }
        int count = 0;
        LocalDate date = start;
        while (!date.isAfter(end)) {
            if (date.getDayOfWeek() != DayOfWeek.MONDAY) {
                count++;
            }
            date = date.plusDays(1);
        }
        return count;
    }

    private void putFallbackIfZero(
            Map<BeanType, BigDecimal> map,
            BeanType type,
            BigDecimal fallbackKg
    ) {
        if (map.getOrDefault(type, ZERO).signum() == 0) {
            map.put(type, fallbackKg);
        }
    }

    private BigDecimal expenseByName(
            List<MonthlyExpenseItemService.ExpenseItemView> items,
            String target
    ) {
        return items.stream()
                .filter(item -> normalize(item.itemName()).equals(target))
                .map(MonthlyExpenseItemService.ExpenseItemView::amount)
                .reduce(ZERO, BigDecimal::add);
    }

    private ProductSpec productSpec(String normalizedItemName) {
        if (normalizedItemName.contains("두절")) {
            return new ProductSpec(BeanType.LARGE, BigDecimal.ONE, false, false);
        }
        if (normalizedItemName.contains("숙주")) {
            return new ProductSpec(BeanType.MUNG, new BigDecimal("3.5"), true, true);
        }
        if (normalizedItemName.contains("소립")) {
            return new ProductSpec(BeanType.SMALL, new BigDecimal("10"), false, true);
        }
        if (normalizedItemName.contains("곱슬")) {
            boolean smallBox = normalizedItemName.contains("3.5");
            return new ProductSpec(
                    BeanType.SMALL,
                    smallBox ? new BigDecimal("3.5") : new BigDecimal("10"),
                    smallBox,
                    true
            );
        }
        if (normalizedItemName.contains("일반")) {
            boolean smallBox = normalizedItemName.contains("3.5");
            return new ProductSpec(
                    BeanType.MEDIUM,
                    smallBox ? new BigDecimal("3.5") : new BigDecimal("10"),
                    smallBox,
                    true
            );
        }
        return null;
    }

    private Map<BeanType, BigDecimal> emptyBeanMap() {
        Map<BeanType, BigDecimal> result = new EnumMap<>(BeanType.class);
        for (BeanType type : BeanType.values()) {
            result.put(type, ZERO);
        }
        return result;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private record ProductSpec(
            BeanType beanType,
            BigDecimal kgPerUnit,
            boolean box,
            boolean vinyl
    ) {
    }

    private static final class VendorAccumulator {
        private final String vendorName;
        private BigDecimal sales = ZERO;
        private BigDecimal finishedKg = ZERO;
        private BigDecimal boxUnits = ZERO;
        private BigDecimal vinylUnits = ZERO;
        private BigDecimal tofuResaleQty = ZERO;
        private BigDecimal tofuPurchaseCost = ZERO;
        private final Map<BeanType, BigDecimal> finishedKgByBean = new EnumMap<>(BeanType.class);

        private VendorAccumulator(String vendorName) {
            this.vendorName = vendorName;
        }
    }
}
