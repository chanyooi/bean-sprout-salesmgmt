package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.PaymentCycle;
import com.example.salesmgmt.entity.*;
import com.example.salesmgmt.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({PaymentService.class, WeeklyPaymentService.class, WeeklyPaymentAllocationService.class,
        MonthlySalesReportService.class, ReceivableBillingAdjustmentService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentConcurrencyTest {
    @Autowired PaymentService monthly;
    @Autowired WeeklyPaymentService weekly;
    @Autowired VendorRepository vendors;
    @Autowired VendorProfileRepository profiles;
    @Autowired SalesOrderRepository orders;
    @Autowired SalesItemRepository items;
    @Autowired PaymentRepository payments;
    @Autowired WeeklyPaymentRepository weeklyPayments;
    @Autowired PlatformTransactionManager manager;
    final LocalDate day = LocalDate.of(2026,9,7);

    Long seed(PaymentCycle cycle) {
        return new TransactionTemplate(manager).execute(status -> {
            var vendor = vendors.save(new VendorEntity("concurrent", "concurrent", true));
            var profile = new VendorProfileEntity(vendor);
            profile.update(true, null, null, null, null, cycle, null, null, null);
            profiles.save(profile);
            var order = orders.save(new SalesOrderEntity("test-001", day, vendor, null, null, null, "test", 1));
            items.save(new SalesItemEntity(order, "일반콩나물", BigDecimal.ONE, new BigDecimal("100000")));
            return vendor.getId();
        });
    }

    void race(Runnable first, Runnable second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> run(ready, start, first));
            var b = pool.submit(() -> run(ready, start, second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(a.get(20, TimeUnit.SECONDS) + b.get(20, TimeUnit.SECONDS)).isEqualTo(1);
        }
    }

    int run(CountDownLatch ready, CountDownLatch start, Runnable action) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start timed out");
            action.run();
            return 1;
        } catch (IllegalArgumentException alreadyPaid) {
            assertThat(alreadyPaid.getMessage()).containsAnyOf("이미 입금", "입금 완료 처리할 미수 거래처가 없습니다");
            return 0;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    @Test void concurrentMonthlyCompletionCreatesOneReceipt() throws Exception {
        Long id = seed(PaymentCycle.MONTHLY);
        Runnable complete = () -> monthly.completeOutstandingPayment(YearMonth.from(day), id, day);
        race(complete, complete);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(monthly.createMonthlyReport(YearMonth.from(day)).outstandingAmount()).isEqualByComparingTo("0");
    }

    @Test void concurrentWeeklyCompletionCreatesOneReceipt() throws Exception {
        Long id = seed(PaymentCycle.WEEKLY);
        Runnable complete = () -> weekly.completeOutstanding(day, id, day);
        race(complete, complete);
        assertThat(weeklyPayments.count()).isEqualTo(1);
        assertThat(monthly.createMonthlyReport(YearMonth.from(day)).outstandingAmount()).isEqualByComparingTo("0");
    }

    @Test void bulkAndIndividualCompletionCannotDoublePay() throws Exception {
        Long id = seed(PaymentCycle.MONTHLY);
        race(() -> monthly.completeAllOutstandingPayments(YearMonth.from(day), day),
                () -> monthly.completeOutstandingPayment(YearMonth.from(day), id, day));
        assertThat(payments.count()).isEqualTo(1);
    }
}
