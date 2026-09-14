package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.WeeklyPaymentEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

/** Allocate one payment without creating a second payment record. */
public final class WeeklyPaymentAllocation {
    private WeeklyPaymentAllocation() {}
    public static BigDecimal forMonth(WeeklyPaymentEntity payment, YearMonth month, List<SalesItemEntity> items) {
        YearMonth first = YearMonth.from(payment.getWeekStart());
        YearMonth last = YearMonth.from(payment.getWeekStart().plusDays(6));
        if (!month.equals(first) && !month.equals(last)) return BigDecimal.ZERO;
        if (first.equals(last)) return payment.getAmount();
        BigDecimal firstSales = BigDecimal.ZERO;
        BigDecimal lastSales = BigDecimal.ZERO;
        for (SalesItemEntity item : items) {
            if (YearMonth.from(item.getSalesOrder().getDeliveryDate()).equals(first)) {
                firstSales = firstSales.add(BillingAmountPolicy.amount(item));
            } else {
                lastSales = lastSales.add(BillingAmountPolicy.amount(item));
            }
        }
        // Net credits do not receive positive payment allocations. With no positive bill,
        // put the payment in the closing month so it is not lost or counted twice.
        firstSales = firstSales.max(BigDecimal.ZERO);
        lastSales = lastSales.max(BigDecimal.ZERO);
        BigDecimal total = firstSales.add(lastSales);
        BigDecimal firstPaid = total.signum() == 0 ? BigDecimal.ZERO
                : payment.getAmount().multiply(firstSales).divide(total, 2, RoundingMode.HALF_UP);
        return month.equals(first) ? firstPaid : payment.getAmount().subtract(firstPaid);
    }
}
