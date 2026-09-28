package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.PaymentCycle;
import com.example.salesmgmt.entity.*;
import com.example.salesmgmt.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({PaymentService.class, WeeklyPaymentService.class, WeeklyPaymentAllocationService.class,
        MonthlySalesReportService.class, ReceivableBillingAdjustmentService.class,
        PriceManagementService.class, MonthlyCloseService.class})
class PaymentAccountingTest {
    @Autowired PaymentService monthly;
    @Autowired WeeklyPaymentService weekly;
    @Autowired PriceManagementService prices;
    @Autowired MonthlyCloseService closing;
    @Autowired VendorRepository vendors;
    @Autowired VendorProfileRepository profiles;
    @Autowired SalesOrderRepository orders;
    @Autowired SalesItemRepository items;
    @Autowired VendorPriceRepository priceRepository;
    @Autowired WeeklyPaymentRepository weeklyPayments;
    @Autowired PaymentRepository payments;
    final YearMonth september = YearMonth.of(2026, 9);

    VendorEntity vendor(String name, PaymentCycle cycle) {
        var vendor = vendors.save(new VendorEntity(name, name, true));
        var profile = new VendorProfileEntity(vendor);
        profile.update(true, null, null, null, null, cycle, null, null, null);
        profiles.save(profile);
        return vendor;
    }

    SalesItemEntity sale(VendorEntity vendor, String date, String amount) {
        var order = orders.save(new SalesOrderEntity(date + "-" + vendor.getId(), LocalDate.parse(date),
                vendor, null, null, null, "test", 1));
        return items.save(new SalesItemEntity(order, "일반콩나물", BigDecimal.ONE,
                amount == null ? null : new BigDecimal(amount)));
    }

    @Test void weeklyReceiptsAppearInMonthlyTotalsAndDeletionIsReflected() {
        var vendor = vendor("weekly", PaymentCycle.WEEKLY);
        sale(vendor, "2026-09-07", "100000");
        weekly.addPayment(LocalDate.of(2026,9,6), vendor.getId(), LocalDate.of(2026,9,12),
                new BigDecimal("60000"), "partial");
        var report = monthly.createMonthlyReport(september);
        assertThat(report.paidAmount()).isEqualByComparingTo("60000");
        assertThat(report.outstandingAmount()).isEqualByComparingTo("40000");
        assertThat(report.paymentRows()).hasSize(1);
        assertThat(report.paymentRows().getFirst().weekStart()).isEqualTo(LocalDate.of(2026,9,6));
        weekly.deletePayment(report.paymentRows().getFirst().paymentId());
        assertThat(monthly.createMonthlyReport(september).paidAmount()).isEqualByComparingTo("0");
    }

    @Test void crossingMonthReceiptsAreAllocatedAndPreserveEveryCent() {
        var vendor = vendor("boundary", PaymentCycle.WEEKLY);
        sale(vendor, "2026-08-31", "100");
        sale(vendor, "2026-09-01", "200");
        weekly.addPayment(LocalDate.of(2026,8,30), vendor.getId(), LocalDate.of(2026,9,5),
                new BigDecimal("100"), null);
        assertThat(monthly.createMonthlyReport(YearMonth.of(2026,8)).paidAmount()).isEqualByComparingTo("33.33");
        assertThat(monthly.createMonthlyReport(september).paidAmount()).isEqualByComparingTo("66.67");
    }

    @Test void weeklyVendorsCannotBeCompletedAgainFromMonthlyScreen() {
        var vendor = vendor("weekly", PaymentCycle.WEEKLY);
        sale(vendor, "2026-09-07", "100");
        assertThatThrownBy(() -> monthly.completeOutstandingPayment(september, vendor.getId(), LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("주별");
        assertThat(payments.count()).isZero();
    }

    @Test void bulkCompletionExcludesWeeklyVendors() {
        var week = vendor("weekly", PaymentCycle.WEEKLY);
        var month = vendor("monthly", PaymentCycle.MONTHLY);
        sale(week, "2026-09-07", "100");
        sale(month, "2026-09-07", "200");
        var result = monthly.completeAllOutstandingPayments(september, LocalDate.now());
        assertThat(result.vendorCount()).isEqualTo(1);
        assertThat(result.totalAmount()).isEqualByComparingTo("200");
        assertThat(payments.findAll()).extracting(p -> p.getVendor().getId()).containsExactly(month.getId());
    }

    @Test void closedMonthRejectsPriceChangeBeforeMutatingBasePrice() {
        var vendor = vendor("closed", PaymentCycle.MONTHLY);
        var item = sale(vendor, "2026-09-07", "100");
        var price = priceRepository.save(new VendorPriceEntity(vendor, "일반콩나물", new BigDecimal("100"), "test"));
        closing.close(september);
        assertThatThrownBy(() -> prices.updatePriceForMonth(price.getId(), new BigDecimal("200"), september))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("마감");
        assertThat(price.getUnitPrice()).isEqualByComparingTo("100");
        assertThat(item.getLineAmount()).isEqualByComparingTo("100");
    }

    @Test void automaticPriceFillingSkipsClosedMonthsButFillsOpenMonths() {
        var vendor = vendor("unpriced", PaymentCycle.MONTHLY);
        var closed = sale(vendor, "2026-08-31", null);
        var open = sale(vendor, "2026-09-01", null);
        closing.close(YearMonth.of(2026,8));
        prices.createOrUpdatePrice(vendor.getId(), "일반콩나물", new BigDecimal("100"));
        assertThat(closed.getUnitPrice()).isNull();
        assertThat(open.getLineAmount()).isEqualByComparingTo("100");
    }

    @Test void openMonthStillRepricesExistingSales() {
        var vendor = vendor("open", PaymentCycle.MONTHLY);
        var item = sale(vendor, "2026-09-07", "100");
        var price = priceRepository.save(new VendorPriceEntity(vendor, "일반콩나물", new BigDecimal("100"), "test"));
        prices.updatePriceForMonth(price.getId(), new BigDecimal("200"), september);
        assertThat(item.getLineAmount()).isEqualByComparingTo("200");
    }
}
