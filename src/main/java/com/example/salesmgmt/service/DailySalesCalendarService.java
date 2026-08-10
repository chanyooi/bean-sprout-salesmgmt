package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.DailySalesCalendarView;
import com.example.salesmgmt.domain.EditableSaleRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class DailySalesCalendarService {

    private final SalesManagementService salesManagementService;

    public DailySalesCalendarService(
            SalesManagementService salesManagementService
    ) {
        this.salesManagementService = salesManagementService;
    }

    @Transactional(readOnly = true)
    public DailySalesCalendarView create(
            String requestedMonth,
            String requestedDate
    ) {
        YearMonth month =
                salesManagementService.resolveMonth(requestedMonth);

        List<EditableSaleRow> rows =
                salesManagementService.findRows(month, null);

        Map<LocalDate, List<EditableSaleRow>> rowsByDate =
                new HashMap<>();

        for (EditableSaleRow row : rows) {
            rowsByDate.computeIfAbsent(
                    row.deliveryDate(),
                    ignored -> new ArrayList<>()
            ).add(row);
        }

        BigDecimal monthlySales = BigDecimal.ZERO;
        Set<Long> monthlyOrders = new HashSet<>();
        Set<Long> monthlyVendors = new HashSet<>();
        long missingPriceCount = 0;
        long salesDayCount = 0;

        for (Map.Entry<LocalDate, List<EditableSaleRow>> entry
                : rowsByDate.entrySet()) {

            boolean hasAnySale =
                    !entry.getValue().isEmpty();

            if (hasAnySale) {
                salesDayCount++;
            }

            for (EditableSaleRow row : entry.getValue()) {
                monthlyOrders.add(row.orderId());
                monthlyVendors.add(row.vendorId());

                if (row.lineAmount() == null) {
                    missingPriceCount++;
                } else {
                    monthlySales =
                            monthlySales.add(row.lineAmount());
                }
            }
        }

        BigDecimal averageSales =
                salesDayCount == 0
                        ? BigDecimal.ZERO
                        : monthlySales.divide(
                                BigDecimal.valueOf(salesDayCount),
                                0,
                                RoundingMode.HALF_UP
                        );

        LocalDate firstOfMonth = month.atDay(1);
        LocalDate lastOfMonth = month.atEndOfMonth();

        int daysBackToSunday =
                firstOfMonth.getDayOfWeek().getValue() % 7;

        LocalDate calendarStart =
                firstOfMonth.minusDays(daysBackToSunday);

        int lastDayIndex =
                lastOfMonth.getDayOfWeek().getValue() % 7;

        LocalDate calendarEnd =
                lastOfMonth.plusDays(6L - lastDayIndex);

        List<List<DailySalesCalendarView.DayCell>> weeks =
                new ArrayList<>();

        List<DailySalesCalendarView.DayCell> week =
                new ArrayList<>(7);

        for (
                LocalDate date = calendarStart;
                !date.isAfter(calendarEnd);
                date = date.plusDays(1)
        ) {
            List<EditableSaleRow> dayRows =
                    rowsByDate.getOrDefault(
                            date,
                            List.of()
                    );

            BigDecimal daySales = BigDecimal.ZERO;
            Set<Long> orderIds = new HashSet<>();
            Set<Long> vendorIds = new HashSet<>();
            long dayMissing = 0;

            for (EditableSaleRow row : dayRows) {
                orderIds.add(row.orderId());
                vendorIds.add(row.vendorId());

                if (row.lineAmount() == null) {
                    dayMissing++;
                } else {
                    daySales = daySales.add(
                            row.lineAmount()
                    );
                }
            }

            week.add(
                    new DailySalesCalendarView.DayCell(
                            date,
                            YearMonth.from(date).equals(month),
                            daySales,
                            orderIds.size(),
                            vendorIds.size(),
                            dayMissing,
                            !dayRows.isEmpty()
                    )
            );

            if (week.size() == 7) {
                weeks.add(List.copyOf(week));
                week.clear();
            }
        }

        LocalDate selectedDate =
                resolveSelectedDate(
                        requestedDate,
                        month
                );

        List<EditableSaleRow> selectedRows =
                selectedDate == null
                        ? List.of()
                        : rowsByDate
                        .getOrDefault(
                                selectedDate,
                                List.of()
                        )
                        .stream()
                        .sorted(
                                Comparator
                                        .comparing(
                                                EditableSaleRow::inputVendor
                                        )
                                        .thenComparing(
                                                EditableSaleRow::orderNumber
                                        )
                                        .thenComparing(
                                                EditableSaleRow::item
                                        )
                        )
                        .toList();

        DailySalesCalendarView.DaySummary selectedDay =
                selectedDate == null
                        ? null
                        : createDaySummary(
                                selectedDate,
                                selectedRows
                        );

        return new DailySalesCalendarView(
                month,
                month.minusMonths(1),
                month.plusMonths(1),
                monthlySales,
                salesDayCount,
                averageSales,
                monthlyOrders.size(),
                monthlyVendors.size(),
                missingPriceCount,
                List.copyOf(weeks),
                selectedDate,
                selectedDay,
                selectedRows
        );
    }

    private LocalDate resolveSelectedDate(
            String requestedDate,
            YearMonth month
    ) {
        if (
                requestedDate == null
                        || requestedDate.isBlank()
        ) {
            return null;
        }

        try {
            LocalDate date =
                    LocalDate.parse(requestedDate);

            if (!YearMonth.from(date).equals(month)) {
                throw new IllegalArgumentException(
                        "선택 날짜는 조회 중인 월 안에 있어야 합니다."
                );
            }

            return date;
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "날짜 형식이 올바르지 않습니다."
            );
        }
    }

    private DailySalesCalendarView.DaySummary createDaySummary(
            LocalDate date,
            List<EditableSaleRow> rows
    ) {
        BigDecimal sales = BigDecimal.ZERO;
        Set<Long> orders = new HashSet<>();
        Set<Long> vendors = new HashSet<>();
        long missing = 0;

        for (EditableSaleRow row : rows) {
            orders.add(row.orderId());
            vendors.add(row.vendorId());

            if (row.lineAmount() == null) {
                missing++;
            } else {
                sales = sales.add(
                        row.lineAmount()
                );
            }
        }

        return new DailySalesCalendarView.DaySummary(
                date,
                sales,
                orders.size(),
                vendors.size(),
                rows.size(),
                missing
        );
    }
}
