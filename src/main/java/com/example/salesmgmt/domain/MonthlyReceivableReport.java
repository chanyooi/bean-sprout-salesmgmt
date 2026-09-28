package com.example.salesmgmt.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record MonthlyReceivableReport(
        YearMonth month,
        BigDecimal billedAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        long outstandingVendorCount,
        long missingPriceCount,
        List<VendorRow> vendorRows,
        List<PaymentRow> paymentRows
) {
    public long monthlyOutstandingVendorCount() {
        return vendorRows.stream().filter(row -> row.paymentCycle() != PaymentCycle.WEEKLY
                && row.outstandingAmount().signum() > 0).count();
    }

    public BigDecimal monthlyOutstandingAmount() {
        return vendorRows.stream().filter(row -> row.paymentCycle() != PaymentCycle.WEEKLY)
                .map(VendorRow::outstandingAmount).filter(amount -> amount.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record VendorRow(
            Long vendorId,
            String vendorName,
            PaymentCycle paymentCycle,
            BigDecimal billedAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount
    ) {
        public String status() {
            if (outstandingAmount.signum() <= 0) {
                return "입금완료";
            }
            if (paidAmount.signum() > 0) {
                return "일부입금";
            }
            return "미입금";
        }
    }

    public record PaymentRow(
            Long paymentId,
            LocalDate paymentDate,
            Long vendorId,
            String vendorName,
            BigDecimal amount,
            String note,
            LocalDate weekStart
    ) {
    }
}
