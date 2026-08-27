package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.BeanType;
import com.example.salesmgmt.domain.BeanUsageCostResult;
import com.example.salesmgmt.domain.ExpenseCategory;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public AnalysisResult analyze(
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
            LocalDate deliveryDate = item.getSalesOrder().getDeliveryDate();

            VendorAccumulator vendor = vendors.computeIfAbsent(
                    vendorName,
                    VendorAccumulator::new
            );

            if ("손두부".equals(itemName)) {
                if (statementName.contains("팔공")) {
                    // 팔공 손두부 행은 매입원가이므로 거래처 매출/배송 횟수에서 제외한다.
                    continue;
                }
                vendor.sales = vendor.sales.add(lineAmount);
                vendor.tofuResaleQty = vendor.tofuResaleQty.add(quantity.abs());
                totalTofuResaleQty = totalTofuResaleQty.add(quantity.abs());
                addDeliveryDate(vendor, deliveryDate);
                continue;
            }

            if ("두부판".equals(itemName)) {
                // 두부판은 팔공 반납 수익으로 별도 계산하며 거래처 배송비 배부에는 넣지 않는다.
                continue;
            }

            // 회수통 등 비생산 항목도 기존 회계 기준대로 매출에는 포함한다.
            vendor.sales = vendor.sales.add(lineAmount);
            addDeliveryDate(vendor, deliveryDate);

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

        CostPools pools = classifyCostPools(expenseItems);
        BigDecimal vinylCostPerUnit = totalVinylUnits.signum() == 0
                ? ZERO
                : pools.vinylExpense().divide(totalVinylUnits, 8, RoundingMode.HALF_UP);
        BigDecimal packagingOverheadPerUnit = totalVinylUnits.signum() == 0
                ? ZERO
                : pools.packagingOverhead().divide(totalVinylUnits, 8, RoundingMode.HALF_UP);

        int totalDeliveryCount = vendors.values().stream()
                .mapToInt(vendor -> vendor.deliveryDates.size())
                .sum();

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
            BigDecimal directProfit = vendor.sales.subtract(directCost);

            // 인건비·월세·전기/수도 같은 생산 공통비는 판매중량 비중으로 배부한다.
            BigDecimal productionOverhead = totalFinishedKg.signum() == 0
                    ? ZERO
                    : pools.productionOverhead().multiply(
                            vendor.finishedKg.divide(totalFinishedKg, 10, RoundingMode.HALF_UP)
                    );

            // 차량·배송비는 거래처에 실제 납품한 날짜 수(배송 stop) 비중으로 배부한다.
            BigDecimal deliveryOverhead = totalDeliveryCount == 0
                    ? ZERO
                    : pools.deliveryOverhead().multiply(
                            BigDecimal.valueOf(vendor.deliveryDates.size())
                                    .divide(BigDecimal.valueOf(totalDeliveryCount), 10, RoundingMode.HALF_UP)
                    );

            // 박스·비닐 외 포장 소모품은 실제 포장 개수 비중으로 배부한다.
            BigDecimal packagingOverhead = vendor.vinylUnits.multiply(packagingOverheadPerUnit);

            BigDecimal allocatedOverhead = productionOverhead
                    .add(deliveryOverhead)
                    .add(packagingOverhead);
            BigDecimal totalCost = directCost.add(allocatedOverhead);
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
                    money(directProfit),
                    money(productionOverhead),
                    money(deliveryOverhead),
                    money(packagingOverhead),
                    money(allocatedOverhead),
                    money(totalCost),
                    money(profit),
                    margin,
                    vendor.deliveryDates.size()
            ));
        }

        List<MonthlyProfitReport.VendorProfitRow> sortedRows = rows.stream()
                .sorted(Comparator
                        .comparing(MonthlyProfitReport.VendorProfitRow::sales)
                        .reversed()
                        .thenComparing(MonthlyProfitReport.VendorProfitRow::vendorName))
                .toList();

        return new AnalysisResult(
                sortedRows,
                money(pools.unallocatedCompanyExpense())
        );
    }

    /**
     * 기존 호출부 호환용. 새 손익 화면에서는 analyze()의 회사 공통비 정보까지 사용한다.
     */
    @Transactional(readOnly = true)
    public List<MonthlyProfitReport.VendorProfitRow> createRows(
            YearMonth month,
            BeanUsageCostResult beanCost
    ) {
        return analyze(month, beanCost).rows();
    }

    private CostPools classifyCostPools(
            List<MonthlyExpenseItemService.ExpenseItemView> items
    ) {
        BigDecimal vinyl = ZERO;
        BigDecimal boxEntered = ZERO;
        BigDecimal production = ZERO;
        BigDecimal delivery = ZERO;
        BigDecimal packaging = ZERO;
        BigDecimal companyCommon = ZERO;

        for (MonthlyExpenseItemService.ExpenseItemView item : items) {
            BigDecimal amount = nz(item.amount());
            ExpenseCategory category = item.category();
            String name = normalize(item.itemName());

            if (category == ExpenseCategory.PACKAGING) {
                if ("비닐".equals(name)) {
                    vinyl = vinyl.add(amount);
                } else if ("박스".equals(name)) {
                    // 박스는 판매수량 x 400원으로 직접 계산하므로 입력 합계는 거래처 배부에서 제외한다.
                    boxEntered = boxEntered.add(amount);
                } else {
                    packaging = packaging.add(amount);
                }
                continue;
            }

            if (category == ExpenseCategory.PERSONNEL || category == ExpenseCategory.FACILITY) {
                production = production.add(amount);
            } else if (category == ExpenseCategory.DELIVERY) {
                delivery = delivery.add(amount);
            } else if (category == ExpenseCategory.WELFARE || category == ExpenseCategory.OTHER) {
                // 식비·기타 비용은 특정 거래처가 발생시킨 비용으로 보기 어려워 회사 공통비로 남긴다.
                companyCommon = companyCommon.add(amount);
            }
        }

        return new CostPools(
                vinyl,
                boxEntered,
                production,
                delivery,
                packaging,
                companyCommon
        );
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

    private void addDeliveryDate(VendorAccumulator vendor, LocalDate deliveryDate) {
        if (deliveryDate != null) {
            vendor.deliveryDates.add(deliveryDate);
        }
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

    public record AnalysisResult(
            List<MonthlyProfitReport.VendorProfitRow> rows,
            BigDecimal unallocatedCompanyExpense
    ) {
    }

    private record CostPools(
            BigDecimal vinylExpense,
            BigDecimal boxExpenseEntered,
            BigDecimal productionOverhead,
            BigDecimal deliveryOverhead,
            BigDecimal packagingOverhead,
            BigDecimal unallocatedCompanyExpense
    ) {
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
        private final Set<LocalDate> deliveryDates = new LinkedHashSet<>();

        private VendorAccumulator(String vendorName) {
            this.vendorName = vendorName;
        }
    }
}
