package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.MonthlyReceivableReport.PaymentRow;
import com.example.salesmgmt.entity.WeeklyPaymentEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import com.example.salesmgmt.repository.WeeklyPaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Weekly receipts remain in their original ledger; this is a monthly reporting projection. */
@Service
public class WeeklyPaymentAllocationService {
    private final WeeklyPaymentRepository payments;
    private final SalesItemRepository sales;

    public WeeklyPaymentAllocationService(WeeklyPaymentRepository payments, SalesItemRepository sales) {
        this.payments = payments;
        this.sales = sales;
    }

    @Transactional(readOnly = true)
    public List<PaymentRow> forMonth(YearMonth month) {
        List<PaymentRow> rows = new ArrayList<>();
        Map<String, BigDecimal> shareCache = new HashMap<>();
        for (WeeklyPaymentEntity payment : payments.findOverlappingWeeks(month.atDay(1).minusDays(6), month.atEndOfMonth())) {
            LocalDate start = payment.getWeekStart();
            YearMonth first = YearMonth.from(start);
            YearMonth last = YearMonth.from(start.plusDays(6));
            BigDecimal amount = payment.getAmount();
            if (!first.equals(last)) {
                String key = payment.getVendor().getId() + ":" + start;
                BigDecimal firstShare = shareCache.computeIfAbsent(key, ignored -> {
                    BigDecimal firstSales = BigDecimal.ZERO;
                    BigDecimal lastSales = BigDecimal.ZERO;
                    for (var item : sales.findForVendorPeriod(payment.getVendor().getId(), start, start.plusDays(6))) {
                        if (item.getLineAmount() == null) continue;
                        if (YearMonth.from(item.getSalesOrder().getDeliveryDate()).equals(first)) {
                            firstSales = firstSales.add(ReceivableBillingAdjustmentService.billingAmount(item));
                        } else {
                            lastSales = lastSales.add(ReceivableBillingAdjustmentService.billingAmount(item));
                        }
                    }
                    firstSales = firstSales.max(BigDecimal.ZERO);
                    lastSales = lastSales.max(BigDecimal.ZERO);
                    BigDecimal total = firstSales.add(lastSales);
                    // With no positive sales, assign the receipt to the week-ending month.
                    return total.signum() == 0 ? BigDecimal.ZERO
                            : firstSales.divide(total, 18, RoundingMode.HALF_UP);
                });
                BigDecimal firstAmount = amount.multiply(firstShare).setScale(2, RoundingMode.HALF_UP);
                amount = month.equals(first) ? firstAmount : amount.subtract(firstAmount);
            }
            if (amount.signum() == 0) continue;
            rows.add(new PaymentRow(payment.getId(), payment.getPaymentDate(), payment.getVendor().getId(),
                    payment.getVendor().getInputName(), amount,
                    "주별 입금 (" + start + " ~ " + start.plusDays(6) + ")"
                            + (payment.getNote() == null ? "" : " · " + payment.getNote()), start));
        }
        return List.copyOf(rows);
    }
}
