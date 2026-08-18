package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.BeanType;
import com.example.salesmgmt.entity.BeanUsageEntity;
import com.example.salesmgmt.repository.BeanUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
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
            accumulator.add(usage.getBeanType(), usage.getBagCount());
        }

        int mondayBasedOffset = first.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        if (mondayBasedOffset < 0) {
            mondayBasedOffset += 7;
        }

        LocalDate gridStart = first.minusDays(mondayBasedOffset);
        List<CalendarCell> cells = new ArrayList<>(42);

        for (int i = 0; i < 42; i++) {
            LocalDate date = gridStart.plusDays(i);
            DailyAccumulator values = daily.getOrDefault(date, new DailyAccumulator());

            cells.add(new CalendarCell(
                    date,
                    date.getDayOfMonth(),
                    YearMonth.from(date).equals(month),
                    normalized(values.large),
                    normalized(values.medium),
                    normalized(values.small)
            ));
        }

        BigDecimal monthLarge = ZERO;
        BigDecimal monthMedium = ZERO;
        BigDecimal monthSmall = ZERO;
        for (DailyAccumulator accumulator : daily.values()) {
            monthLarge = monthLarge.add(accumulator.large);
            monthMedium = monthMedium.add(accumulator.medium);
            monthSmall = monthSmall.add(accumulator.small);
        }

        return new BeanUsageCalendarData(
                month,
                List.copyOf(cells),
                normalized(monthLarge),
                normalized(monthMedium),
                normalized(monthSmall)
        );
    }

    private BigDecimal normalized(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return ZERO;
        }
        return value.stripTrailingZeros();
    }

    private static final class DailyAccumulator {
        private BigDecimal large = ZERO;
        private BigDecimal medium = ZERO;
        private BigDecimal small = ZERO;

        private void add(BeanType type, BigDecimal bags) {
            if (bags == null) {
                return;
            }
            switch (type) {
                case LARGE -> large = large.add(bags);
                case MEDIUM -> medium = medium.add(bags);
                case SMALL -> small = small.add(bags);
                case MUNG -> { }
            }
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
            BigDecimal smallBags
    ) {
        public boolean hasUsage() {
            return largeBags.signum() != 0
                    || mediumBags.signum() != 0
                    || smallBags.signum() != 0;
        }
    }
}
