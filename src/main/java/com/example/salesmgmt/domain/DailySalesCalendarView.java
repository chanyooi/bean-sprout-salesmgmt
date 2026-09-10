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
        List<EditableSaleRow> selectedRows,
        List<VendorDaySummary> selectedVendors
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
            long missingPriceCount,
            String dayTypeLabel,
            BigDecimal comparableAverageSales,
            long comparableDayCount,
            BigDecimal deviationPercent,
            String anomalyLevel
    ) {
        public boolean hasComparableAverage() {
            return comparableDayCount > 0 && comparableAverageSales != null;
        }

        public boolean isAnomaly() {
            return "HIGH".equals(anomalyLevel) || "LOW".equals(anomalyLevel);
        }
    }

    public record VendorDaySummary(
            Long vendorId,
            String vendorName,
            BigDecimal salesAmount,
            long orderCount,
            long missingPriceCount,
            List<VendorItemSummary> items
    ) {
    }

    public record VendorItemSummary(
            String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineAmount
    ) {
    }
}
