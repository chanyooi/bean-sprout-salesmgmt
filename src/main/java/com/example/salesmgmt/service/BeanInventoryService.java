package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.BeanCatalog;
import com.example.salesmgmt.domain.BeanInventoryView;
import com.example.salesmgmt.domain.BeanOrigin;
import com.example.salesmgmt.domain.BeanPurchaseRow;
import com.example.salesmgmt.domain.BeanStockSummaryRow;
import com.example.salesmgmt.domain.BeanType;
import com.example.salesmgmt.domain.BeanUsageCostResult;
import com.example.salesmgmt.domain.BeanUsageRow;
import com.example.salesmgmt.entity.BeanPurchaseEntity;
import com.example.salesmgmt.entity.BeanStockSettingEntity;
import com.example.salesmgmt.entity.BeanUsageEntity;
import com.example.salesmgmt.repository.BeanPurchaseRepository;
import com.example.salesmgmt.repository.BeanStockSettingRepository;
import com.example.salesmgmt.repository.BeanUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BeanInventoryService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal KG_PER_BAG = new BigDecimal("25");
    private static final BigDecimal DEFAULT_LOW_STOCK_BAGS = new BigDecimal("3");

    private final BeanPurchaseRepository beanPurchaseRepository;
    private final BeanUsageRepository beanUsageRepository;
    private final BeanStockSettingRepository beanStockSettingRepository;

    public BeanInventoryService(
            BeanPurchaseRepository beanPurchaseRepository,
            BeanUsageRepository beanUsageRepository,
            BeanStockSettingRepository beanStockSettingRepository
    ) {
        this.beanPurchaseRepository = beanPurchaseRepository;
        this.beanUsageRepository = beanUsageRepository;
        this.beanStockSettingRepository = beanStockSettingRepository;
    }

    @Transactional
    public void addPurchase(
            LocalDate purchaseDate,
            BeanType beanType,
            BeanOrigin origin,
            BigDecimal bagCount,
            BigDecimal unitPricePerBag,
            String note
    ) {
        validateCombination(beanType, origin);
        beanPurchaseRepository.save(new BeanPurchaseEntity(
                purchaseDate,
                beanType,
                origin,
                bagCount,
                unitPricePerBag,
                note
        ));
    }

    @Transactional
    public void addUsage(
            LocalDate usageDate,
            BeanType beanType,
            BeanOrigin origin,
            BigDecimal bagCount,
            String note
    ) {
        validateCombination(beanType, origin);
        beanUsageRepository.save(new BeanUsageEntity(
                usageDate,
                beanType,
                origin,
                bagCount,
                note
        ));
    }

    @Transactional
    public void deletePurchase(Long purchaseId) {
        if (!beanPurchaseRepository.existsById(purchaseId)) {
            throw new IllegalArgumentException("삭제할 매입 기록을 찾을 수 없습니다.");
        }
        beanPurchaseRepository.deleteById(purchaseId);
    }

    @Transactional
    public void deleteUsage(Long usageId) {
        if (!beanUsageRepository.existsById(usageId)) {
            throw new IllegalArgumentException("삭제할 사용 기록을 찾을 수 없습니다.");
        }
        beanUsageRepository.deleteById(usageId);
    }

    @Transactional
    public void updateThreshold(
            BeanType beanType,
            BeanOrigin origin,
            BigDecimal thresholdBags
    ) {
        validateCombination(beanType, origin);

        BeanStockSettingEntity setting = beanStockSettingRepository
                .findByBeanTypeAndOrigin(beanType, origin)
                .orElseGet(() -> new BeanStockSettingEntity(
                        beanType,
                        origin,
                        DEFAULT_LOW_STOCK_BAGS
                ));

        setting.updateThreshold(thresholdBags);
        beanStockSettingRepository.save(setting);
    }

    @Transactional(readOnly = true)
    public BeanInventoryView getInventory(LocalDate asOfDate) {
        LocalDate safeDate = asOfDate == null ? LocalDate.now() : asOfDate;

        List<BeanPurchaseEntity> purchases = beanPurchaseRepository
                .findAllByPurchaseDateLessThanEqualOrderByPurchaseDateAscIdAsc(safeDate);
        List<BeanUsageEntity> usages = beanUsageRepository
                .findAllByUsageDateLessThanEqualOrderByUsageDateAscIdAsc(safeDate);

        Map<StockKey, StockAccumulator> accumulators = new LinkedHashMap<>();
        for (BeanCatalog.BeanCombination combination : BeanCatalog.ALLOWED_COMBINATIONS) {
            StockKey key = new StockKey(combination.beanType(), combination.origin());
            accumulators.put(key, new StockAccumulator());
        }

        for (BeanPurchaseEntity purchase : purchases) {
            StockAccumulator accumulator = accumulators.get(new StockKey(
                    purchase.getBeanType(),
                    purchase.getOrigin()
            ));
            if (accumulator != null) {
                accumulator.purchasedBags = accumulator.purchasedBags.add(purchase.getBagCount());
                accumulator.purchaseAmount = accumulator.purchaseAmount.add(purchase.getTotalAmount());
            }
        }

        for (BeanUsageEntity usage : usages) {
            StockAccumulator accumulator = accumulators.get(new StockKey(
                    usage.getBeanType(),
                    usage.getOrigin()
            ));
            if (accumulator != null) {
                accumulator.usedBags = accumulator.usedBags.add(usage.getBagCount());
            }
        }

        Map<StockKey, BigDecimal> thresholds = new HashMap<>();
        for (BeanStockSettingEntity setting : beanStockSettingRepository.findAll()) {
            thresholds.put(
                    new StockKey(setting.getBeanType(), setting.getOrigin()),
                    setting.getLowStockThresholdBags()
            );
        }

        List<BeanStockSummaryRow> stockRows = new ArrayList<>();
        long lowStockCount = 0;

        for (BeanCatalog.BeanCombination combination : BeanCatalog.ALLOWED_COMBINATIONS) {
            StockKey key = new StockKey(combination.beanType(), combination.origin());
            StockAccumulator accumulator = accumulators.get(key);

            BigDecimal currentBags = accumulator.purchasedBags.subtract(accumulator.usedBags);
            BigDecimal averagePricePerBag = accumulator.purchasedBags.signum() == 0
                    ? ZERO
                    : accumulator.purchaseAmount.divide(
                            accumulator.purchasedBags,
                            2,
                            RoundingMode.HALF_UP
                    );
            BigDecimal estimatedStockValue = currentBags.signum() <= 0
                    ? ZERO
                    : currentBags.multiply(averagePricePerBag)
                            .setScale(2, RoundingMode.HALF_UP);
            BigDecimal threshold = thresholds.getOrDefault(key, DEFAULT_LOW_STOCK_BAGS);
            boolean active = accumulator.purchasedBags.signum() != 0
                    || accumulator.usedBags.signum() != 0;
            boolean lowStock = active && currentBags.compareTo(threshold) <= 0;
            if (lowStock) {
                lowStockCount++;
            }

            stockRows.add(new BeanStockSummaryRow(
                    combination.beanType(),
                    combination.origin(),
                    normalized(accumulator.purchasedBags),
                    kg(accumulator.purchasedBags),
                    normalized(accumulator.usedBags),
                    kg(accumulator.usedBags),
                    normalized(currentBags),
                    kg(currentBags),
                    normalizedMoney(averagePricePerBag),
                    normalizedMoney(estimatedStockValue),
                    normalized(threshold),
                    active,
                    lowStock
            ));
        }

        List<BeanPurchaseRow> recentPurchases = beanPurchaseRepository
                .findTop50ByOrderByPurchaseDateDescIdDesc()
                .stream()
                .map(this::toPurchaseRow)
                .toList();

        List<BeanUsageRow> recentUsages = beanUsageRepository
                .findTop50ByOrderByUsageDateDescIdDesc()
                .stream()
                .map(this::toUsageRow)
                .toList();

        return new BeanInventoryView(
                safeDate,
                List.copyOf(stockRows),
                recentPurchases,
                recentUsages,
                lowStockCount
        );
    }

    @Transactional(readOnly = true)
    public BeanUsageCostResult calculateUsageCost(YearMonth month) {
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();

        List<BeanPurchaseEntity> purchases = beanPurchaseRepository
                .findAllByPurchaseDateLessThanEqualOrderByPurchaseDateAscIdAsc(endDate);
        List<BeanUsageEntity> usages = beanUsageRepository
                .findAllByUsageDateBetweenOrderByUsageDateAscIdAsc(startDate, endDate);

        Map<StockKey, List<BeanPurchaseEntity>> purchasesByKey = new HashMap<>();
        for (BeanPurchaseEntity purchase : purchases) {
            purchasesByKey
                    .computeIfAbsent(
                            new StockKey(purchase.getBeanType(), purchase.getOrigin()),
                            ignored -> new ArrayList<>()
                    )
                    .add(purchase);
        }

        Map<StockKey, UsageCostAccumulator> rowAccumulators = new LinkedHashMap<>();
        BigDecimal totalBags = ZERO;
        BigDecimal totalCost = ZERO;
        long missingCount = 0;

        for (BeanUsageEntity usage : usages) {
            StockKey key = new StockKey(usage.getBeanType(), usage.getOrigin());
            BigDecimal averagePrice = weightedAveragePriceAtDate(
                    purchasesByKey.getOrDefault(key, List.of()),
                    usage.getUsageDate()
            );

            UsageCostAccumulator accumulator = rowAccumulators.computeIfAbsent(
                    key,
                    ignored -> new UsageCostAccumulator()
            );
            accumulator.usedBags = accumulator.usedBags.add(usage.getBagCount());
            totalBags = totalBags.add(usage.getBagCount());

            if (averagePrice == null) {
                accumulator.missingCount++;
                missingCount++;
                continue;
            }

            BigDecimal usageCost = usage.getBagCount()
                    .multiply(averagePrice)
                    .setScale(2, RoundingMode.HALF_UP);
            accumulator.knownCost = accumulator.knownCost.add(usageCost);
            accumulator.knownCostBags = accumulator.knownCostBags.add(usage.getBagCount());
            totalCost = totalCost.add(usageCost);
        }

        List<BeanUsageCostResult.Row> rows = rowAccumulators.entrySet()
                .stream()
                .map(entry -> {
                    StockKey key = entry.getKey();
                    UsageCostAccumulator acc = entry.getValue();
                    BigDecimal effectiveAverage = acc.knownCostBags.signum() == 0
                            ? ZERO
                            : acc.knownCost.divide(
                                    acc.knownCostBags,
                                    2,
                                    RoundingMode.HALF_UP
                            );
                    return new BeanUsageCostResult.Row(
                            key.beanType,
                            key.origin,
                            normalized(acc.usedBags),
                            kg(acc.usedBags),
                            normalizedMoney(acc.knownCost),
                            normalizedMoney(effectiveAverage),
                            acc.missingCount
                    );
                })
                .sorted(Comparator
                        .comparing((BeanUsageCostResult.Row row) -> row.beanType().ordinal())
                        .thenComparing(row -> row.origin().ordinal()))
                .toList();

        return new BeanUsageCostResult(
                normalized(totalBags),
                kg(totalBags),
                normalizedMoney(totalCost),
                missingCount,
                rows
        );
    }

    private BigDecimal weightedAveragePriceAtDate(
            List<BeanPurchaseEntity> purchases,
            LocalDate usageDate
    ) {
        BigDecimal bags = ZERO;
        BigDecimal amount = ZERO;

        for (BeanPurchaseEntity purchase : purchases) {
            if (purchase.getPurchaseDate().isAfter(usageDate)) {
                break;
            }
            bags = bags.add(purchase.getBagCount());
            amount = amount.add(purchase.getTotalAmount());
        }

        if (bags.signum() == 0) {
            return null;
        }

        return amount.divide(bags, 2, RoundingMode.HALF_UP);
    }

    private void validateCombination(BeanType beanType, BeanOrigin origin) {
        if (beanType == null || origin == null) {
            throw new IllegalArgumentException("콩 종류와 원산지를 선택해주세요.");
        }
        if (!BeanCatalog.isAllowed(beanType, origin)) {
            throw new IllegalArgumentException(
                    beanType.getLabel() + "에는 " + origin.getLabel()
                            + "을 등록할 수 없습니다."
            );
        }
    }

    private BeanPurchaseRow toPurchaseRow(BeanPurchaseEntity entity) {
        return new BeanPurchaseRow(
                entity.getId(),
                entity.getPurchaseDate(),
                entity.getBeanType(),
                entity.getOrigin(),
                normalized(entity.getBagCount()),
                normalized(entity.getTotalKg()),
                normalizedMoney(entity.getUnitPricePerBag()),
                normalizedMoney(entity.getTotalAmount()),
                entity.getNote()
        );
    }

    private BeanUsageRow toUsageRow(BeanUsageEntity entity) {
        return new BeanUsageRow(
                entity.getId(),
                entity.getUsageDate(),
                entity.getBeanType(),
                entity.getOrigin(),
                normalized(entity.getBagCount()),
                normalized(entity.getTotalKg()),
                entity.getNote()
        );
    }

    private static BigDecimal kg(BigDecimal bagCount) {
        return normalized(bagCount.multiply(KG_PER_BAG));
    }

    private static BigDecimal normalized(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return ZERO;
        }
        return value.stripTrailingZeros();
    }

    private static BigDecimal normalizedMoney(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private record StockKey(BeanType beanType, BeanOrigin origin) {
    }

    private static final class StockAccumulator {
        private BigDecimal purchasedBags = ZERO;
        private BigDecimal purchaseAmount = ZERO;
        private BigDecimal usedBags = ZERO;
    }

    private static final class UsageCostAccumulator {
        private BigDecimal usedBags = ZERO;
        private BigDecimal knownCostBags = ZERO;
        private BigDecimal knownCost = ZERO;
        private long missingCount;
    }
}
