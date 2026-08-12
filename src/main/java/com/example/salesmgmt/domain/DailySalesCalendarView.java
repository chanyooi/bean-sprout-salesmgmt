package com.example.salesmgmt.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record DailySalesCalendarView(
        YearMonth month,
        YearMonth previousMonth,
        YearMonth nextMonth,
        BigDecimal monthlySales,
        BigDecimal monthlyBoxCount,
        long salesDayCount,
        BigDecimal averageSalesPerSalesDay,
        long monthlyOrderCount,
        long monthlyVendorCount,
        long missingPriceCount,
        List<List<DayCell>> weeks,
        LocalDate selectedDate,
        DaySummary selectedDay,
        List<EditableSaleRow> selectedRows
) {
    public record DayCell(
            LocalDate date,
            boolean inCurrentMonth,
            BigDecimal salesAmount,
            BigDecimal boxCount,
            long orderCount,
            long vendorCount,
            long missingPriceCount,
            boolean hasSales
    ) {
        public boolean isSunday() {
            return date.getDayOfWeek().getValue() == 7;
        }

        public boolean isSaturday() {
            return date.getDayOfWeek().getValue() == 6;
        }
    }

    public record DaySummary(
            LocalDate date,
            BigDecimal salesAmount,
            BigDecimal dailyBoxCount,
            BigDecimal weeklyBoxCount,
            long orderCount,
            long vendorCount,
            long itemCount,
            long missingPriceCount
    ) {
    }
}
