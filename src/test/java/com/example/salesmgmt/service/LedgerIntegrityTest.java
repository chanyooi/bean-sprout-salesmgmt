package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.*;
import com.example.salesmgmt.entity.*;
import com.example.salesmgmt.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({LedgerIntegrityTest.JsonConfig.class, PriceManagementService.class,
    MonthlyCloseService.class, UploadHistoryService.class, InputWorkbookSnapshotService.class,
    PaymentService.class, MonthlySalesReportService.class, ReceivableBillingAdjustmentService.class,
    StatementFinalBillingPatchService.class})
class LedgerIntegrityTest {
    @TestConfiguration static class JsonConfig {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
    @Autowired PriceManagementService prices;
    @Autowired MonthlyCloseService close;
    @Autowired UploadHistoryService history;
    @Autowired PaymentService payments;
    @Autowired StatementFinalBillingPatchService statements;
    @Autowired VendorRepository vendors;
    @Autowired VendorPriceRepository vendorPrices;
    @Autowired SalesOrderRepository orders;
    @Autowired SalesItemRepository items;
    @Autowired UploadHistoryRepository histories;
    @Autowired InputWorkbookSnapshotRepository originals;
    @Autowired WeeklyPaymentRepository weekly;
    @Autowired PaymentRepository paymentRepository;
    static final YearMonth AUG = YearMonth.of(2026, 8);

    @Test void closedMonthRejectsPriceChange() {
        var v = vendors.save(new VendorEntity("test", "test", true));
        var p = vendorPrices.save(new VendorPriceEntity(v, "일반콩나물", bd("1000"), "test"));
        var item = item(v, "20260801-001", "2026-08-01", "일반콩나물", "10", "1000");
        close.close(AUG);
        assertThatThrownBy(() -> prices.updatePriceForMonth(p.getId(), bd("2000"), AUG))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(item.getLineAmount()).isEqualByComparingTo("10000");
    }

    @Test void fillingMissingPricesLeavesClosedMonthUntouched() {
        var v = vendors.save(new VendorEntity("test", "test", true));
        var old = item(v, "20260801-001", "2026-08-01", "일반콩나물", "1", null);
        var current = item(v, "20260901-001", "2026-09-01", "일반콩나물", "1", null);
        close.close(AUG);
        prices.createOrUpdatePrice(v.getId(), "일반콩나물", bd("1000"));
        assertThat(old.getUnitPrice()).isNull();
        assertThat(current.getLineAmount()).isEqualByComparingTo("1000");
    }

    @Test void restoreRejectsClosedMonth() {
        var v = vendors.save(new VendorEntity("test", "test", true));
        item(v, "20260801-001", "2026-08-01", "일반콩나물", "10", "1000");
        var id = recordBackup();
        close.close(AUG);
        assertThatThrownBy(() -> history.restoreLatest(id)).isInstanceOf(IllegalArgumentException.class);
        assertThat(histories.findById(id).orElseThrow().isRestored()).isFalse();
    }

    @Test void restoreInvalidatesUploadedWorkbook() {
        var v = vendors.save(new VendorEntity("test", "test", true));
        item(v, "20260801-001", "2026-08-01", "일반콩나물", "10", "1000");
        var id = recordBackup();
        originals.save(new InputWorkbookSnapshotEntity("2026-08", "bad.xlsx", 3, new byte[]{1,2,3}));
        originals.save(new InputWorkbookSnapshotEntity("2026-07", "good.xlsx", 1, new byte[]{1}));
        history.restoreLatest(id);
        assertThat(originals.findByMonthKey("2026-08")).isEmpty();
        assertThat(originals.findByMonthKey("2026-07")).isPresent();
    }

    @Test void statementUsesSameSpecialItemBillingAsReceivables() throws Exception {
        var v = vendors.save(new VendorEntity("test", "test", true));
        item(v, "20260801-001", "2026-08-01", "두부판", "10", "1000");
        byte[] source;
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var row = book.createSheet("test").createRow(0);
            row.createCell(0).setCellValue("최종 청구금액");
            row.createCell(1).setCellValue(0);
            book.write(out); source = out.toByteArray();
        }
        var result = statements.patchMonthly(new StatementWorkbookResult(source, "test.xlsx", 1,1,0,0), AUG);
        var report = payments.createMonthlyReport(AUG);
        assertThat(report.billedAmount()).isEqualByComparingTo("20000");
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(result.fileBytes()))) {
            assertThat(book.getSheetAt(0).getRow(0).getCell(1).getNumericCellValue()).isEqualTo(20000);
        }
    }

    @Test void weeklyPaymentReducesMonthlyOutstanding() {
        var v = vendors.save(new VendorEntity("test", "test", true));
        item(v, "20260803-001", "2026-08-03", "일반콩나물", "10", "1000");
        weekly.save(new WeeklyPaymentEntity(v, LocalDate.parse("2026-08-02"), LocalDate.parse("2026-08-08"), bd("10000"), "weekly"));
        var report = payments.createMonthlyReport(AUG);
        assertThat(report.paidAmount()).isEqualByComparingTo("10000");
        assertThat(report.outstandingAmount()).isZero();
        assertThat(report.paymentRows()).hasSize(1);
    }

    @Test void crossMonthWeeklyPaymentIsAllocatedWithoutDuplicatingMoney() {
        var v = vendors.save(new VendorEntity("test", "test", true));
        item(v, "20260831-001", "2026-08-31", "일반콩나물", "1", "1000");
        item(v, "20260901-001", "2026-09-01", "일반콩나물", "3", "1000");
        weekly.save(new WeeklyPaymentEntity(v, LocalDate.parse("2026-08-30"), LocalDate.parse("2026-09-05"), bd("2000"), "partial"));
        assertThat(payments.createMonthlyReport(AUG).paidAmount()).isEqualByComparingTo("500");
        assertThat(payments.createMonthlyReport(AUG.plusMonths(1)).paidAmount()).isEqualByComparingTo("1500");
    }

    private Long recordBackup() {
        var snapshot = new OrderSnapshot("20260801-001", LocalDate.parse("2026-08-01"), "test", "test", null, "", "", "test", 5);
        history.recordSuccess("upload.xlsx", history.captureSalesSnapshot(List.of(snapshot)), new SaveResult(0,0,0,0,0,0,0));
        return history.latestRestorableId();
    }
    private SalesItemEntity item(VendorEntity v, String no, String date, String name, String qty, String price) {
        var order = orders.save(new SalesOrderEntity(no, LocalDate.parse(date), v, null, "", "", "test", 5));
        return items.save(new SalesItemEntity(order, name, bd(qty), price == null ? null : bd(price)));
    }
    static BigDecimal bd(String value) { return new BigDecimal(value); }
}
