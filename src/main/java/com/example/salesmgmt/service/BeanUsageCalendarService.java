package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.BeanType;
import com.example.salesmgmt.entity.BeanUsageEntity;
import com.example.salesmgmt.repository.BeanUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BeanUsageCalendarService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final BeanUsageRepository beanUsageRepository;

    public BeanUsageCalendarService(BeanUsageRepository beanUsageRepository) {
        this.beanUsageRepository = beanUsageRepository;
    }

    @Transactional(readOnly = true)
    public BeanUsageCalendarData load(YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();

        Map<LocalDate, DailyAccumulator> daily = new LinkedHashMap<>();
        List<BeanUsageEntity> usages = beanUsageRepository
                .findAllByUsageDateBetweenOrderByUsageDateAscIdAsc(first, last);

        for (BeanUsageEntity usage : usages) {
            if (usage.getBeanType() == BeanType.MUNG) {
                continue;
            }

            DailyAccumulator accumulator = daily.computeIfAbsent(
                    usage.getUsageDate(),
                    ignored -> new DailyAccumulator()
            );
            accumulator.add(
                    usage.getBeanType(),
                    usage.getBagCount(),
                    usage.getUnitPricePerKg()
            );
        }

        int sundayBasedOffset = first.getDayOfWeek().getValue() % 7;
        LocalDate gridStart = first.minusDays(sundayBasedOffset);
        List<CalendarCell> cells = new ArrayList<>(42);

        for (int i = 0; i < 42; i++) {
            LocalDate date = gridStart.plusDays(i);
            DailyAccumulator values = daily.getOrDefault(date, new DailyAccumulator());

            cells.add(new CalendarCell(
                    date,
                    date.getDayOfMonth(),
                    YearMonth.from(date).equals(month),
                    normalized(values.large.bags),
                    normalized(values.medium.bags),
                    normalized(values.small.bags),
                    normalizedMoney(values.large.averagePricePerKg()),
                    normalizedMoney(values.medium.averagePricePerKg()),
                    normalizedMoney(values.small.averagePricePerKg())
            ));
        }

        BigDecimal monthLarge = ZERO;
        BigDecimal monthMedium = ZERO;
        BigDecimal monthSmall = ZERO;
        for (DailyAccumulator accumulator : daily.values()) {
            monthLarge = monthLarge.add(accumulator.large.bags);
            monthMedium = monthMedium.add(accumulator.medium.bags);
            monthSmall = monthSmall.add(accumulator.small.bags);
        }

        return new BeanUsageCalendarData(
                month,
                List.copyOf(cells),
                normalized(monthLarge),
                normalized(monthMedium),
                normalized(monthSmall)
        );
    }

    @Transactional
    public int deleteDailyUsage(LocalDate usageDate) {
        List<BeanUsageEntity> usages = beanUsageRepository
                .findAllByUsageDateBetweenOrderByUsageDateAscIdAsc(usageDate, usageDate)
                .stream()
                .filter(usage -> usage.getBeanType() != BeanType.MUNG)
                .toList();

        if (usages.isEmpty()) {
            return 0;
        }

        beanUsageRepository.deleteAll(usages);
        return usages.size();
    }

    private BigDecimal normalized(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return ZERO;
        }
        return value.stripTrailingZeros();
    }

    private BigDecimal normalizedMoney(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static final class DailyAccumulator {
        private final TypeAccumulator large = new TypeAccumulator();
        private final TypeAccumulator medium = new TypeAccumulator();
        private final TypeAccumulator small = new TypeAccumulator();

        private void add(BeanType type, BigDecimal bags, BigDecimal unitPricePerKg) {
            if (bags == null) {
                return;
            }
            switch (type) {
                case LARGE -> large.add(bags, unitPricePerKg);
                case MEDIUM -> medium.add(bags, unitPricePerKg);
                case SMALL -> small.add(bags, unitPricePerKg);
                case MUNG -> { }
            }
        }
    }

    private static final class TypeAccumulator {
        private BigDecimal bags = ZERO;
        private BigDecimal pricedBags = ZERO;
        private BigDecimal priceWeight = ZERO;

        private void add(BigDecimal bagCount, BigDecimal unitPricePerKg) {
            bags = bags.add(bagCount);
            if (unitPricePerKg != null && unitPricePerKg.signum() > 0) {
                pricedBags = pricedBags.add(bagCount);
                priceWeight = priceWeight.add(bagCount.multiply(unitPricePerKg));
            }
        }

        private BigDecimal averagePricePerKg() {
            if (pricedBags.signum() == 0) {
                return ZERO;
            }
            return priceWeight.divide(pricedBags, 2, RoundingMode.HALF_UP);
        }
    }

    public record BeanUsageCalendarData(
            YearMonth month,
            List<CalendarCell> cells,
            BigDecimal largeTotal,
            BigDecimal mediumTotal,
            BigDecimal smallTotal
    ) {}

    public record CalendarCell(
            LocalDate date,
            int dayOfMonth,
            boolean inMonth,
            BigDecimal largeBags,
            BigDecimal mediumBags,
            BigDecimal smallBags,
            BigDecimal largePricePerKg,
            BigDecimal mediumPricePerKg,
            BigDecimal smallPricePerKg
    ) {
        public boolean hasUsage() {
            return largeBags.signum() != 0
                    || mediumBags.signum() != 0
                    || smallBags.signum() != 0;
        }
    }
}
