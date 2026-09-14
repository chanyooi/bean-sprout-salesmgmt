package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.MonthlyReceivableReport;
import com.example.salesmgmt.domain.MonthlySalesReport;
import com.example.salesmgmt.domain.PaymentCycle;
import com.example.salesmgmt.entity.PaymentEntity;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.entity.VendorProfileEntity;
import com.example.salesmgmt.repository.PaymentRepository;
import com.example.salesmgmt.repository.VendorProfileRepository;
import com.example.salesmgmt.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String INDIVIDUAL_COMPLETE_NOTE = "입금 완료 자동 처리";
    private static final String BULK_COMPLETE_NOTE = "전체 입금 완료 자동 처리";

    private final com.example.salesmgmt.repository.WeeklyPaymentRepository weeklyPaymentRepository;
    private final com.example.salesmgmt.repository.SalesItemRepository salesItemRepository;
    private final PaymentRepository paymentRepository;
    private final VendorRepository vendorRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final MonthlySalesReportService monthlySalesReportService;
    private final ReceivableBillingAdjustmentService receivableBillingAdjustmentService;

    public PaymentService(
            PaymentRepository paymentRepository,
            VendorRepository vendorRepository,
            VendorProfileRepository vendorProfileRepository,
            MonthlySalesReportService monthlySalesReportService,
            ReceivableBillingAdjustmentService receivableBillingAdjustmentService,
            com.example.salesmgmt.repository.WeeklyPaymentRepository weeklyPaymentRepository,
            com.example.salesmgmt.repository.SalesItemRepository salesItemRepository
    ) {
        this.weeklyPaymentRepository = weeklyPaymentRepository;
        this.salesItemRepository = salesItemRepository;
        this.paymentRepository = paymentRepository;
        this.vendorRepository = vendorRepository;
        this.vendorProfileRepository = vendorProfileRepository;
        this.monthlySalesReportService = monthlySalesReportService;
        this.receivableBillingAdjustmentService = receivableBillingAdjustmentService;
    }

    @Transactional(readOnly = true)
    public YearMonth resolveMonth(String requestedMonth) {
        if (requestedMonth != null && !requestedMonth.isBlank()) {
            try {
                return YearMonth.parse(requestedMonth);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("정산월 형식이 올바르지 않습니다.");
            }
        }

        return monthlySalesReportService.findLatestSalesMonth()
                .orElse(YearMonth.now());
    }

    @Transactional
    public void addPayment(
            YearMonth settlementMonth,
            Long vendorId,
            LocalDate paymentDate,
            BigDecimal amount,
            String note
    ) {
        VendorEntity vendor = vendorRepository.findForPaymentUpdate(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("거래처를 찾을 수 없습니다."));

        assertMonthlyEntry(vendorId);
        paymentRepository.save(new PaymentEntity(
                vendor,
                settlementMonth.toString(),
                paymentDate,
                amount,
                note
        ));
    }

    @Transactional
    public BigDecimal completeOutstandingPayment(
            YearMonth settlementMonth,
            Long vendorId,
            LocalDate paymentDate
    ) {
        VendorEntity vendor = vendorRepository.findForPaymentUpdate(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("거래처를 찾을 수 없습니다."));

        assertMonthlyEntry(vendorId);
        MonthlyReceivableReport report = createMonthlyReport(settlementMonth);

        MonthlyReceivableReport.VendorRow targetRow = report.vendorRows()
                .stream()
                .filter(row -> vendorId.equals(row.vendorId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "해당 월의 청구 또는 입금 내역이 없습니다."
                ));

        BigDecimal outstanding = safe(targetRow.outstandingAmount());
        if (outstanding.signum() <= 0) {
            throw new IllegalArgumentException("이미 입금 완료된 거래처입니다.");
        }

        paymentRepository.save(new PaymentEntity(
                vendor,
                settlementMonth.toString(),
                paymentDate == null ? LocalDate.now() : paymentDate,
                outstanding,
                INDIVIDUAL_COMPLETE_NOTE
        ));

        return money(outstanding);
    }

    @Transactional
    public BulkCompleteResult completeAllOutstandingPayments(
            YearMonth settlementMonth,
            LocalDate paymentDate
    ) {
        vendorRepository.lockAllForPaymentUpdate();
        MonthlyReceivableReport report = createMonthlyReport(settlementMonth);
        LocalDate actualPaymentDate = paymentDate == null ? LocalDate.now() : paymentDate;

        List<Long> targetVendorIds = report.vendorRows().stream()
                .filter(row -> safe(row.outstandingAmount()).signum() > 0)
                .map(MonthlyReceivableReport.VendorRow::vendorId)
                .toList();

        Map<Long, VendorEntity> vendorsById = new HashMap<>();
        vendorRepository.findAllById(targetVendorIds)
                .forEach(vendor -> vendorsById.put(vendor.getId(), vendor));

        long completedCount = 0;
        BigDecimal completedTotal = ZERO;
        List<PaymentEntity> newPayments = new ArrayList<>();

        for (MonthlyReceivableReport.VendorRow row : report.vendorRows()) {
            if (row.paymentCycle() == PaymentCycle.WEEKLY) continue;
            BigDecimal outstanding = safe(row.outstandingAmount());
            if (outstanding.signum() <= 0) {
                continue;
            }

            VendorEntity vendor = vendorsById.get(row.vendorId());
            if (vendor == null) {
                continue;
            }

            newPayments.add(new PaymentEntity(
                    vendor,
                    settlementMonth.toString(),
                    actualPaymentDate,
                    outstanding,
                    BULK_COMPLETE_NOTE
            ));
            completedCount++;
            completedTotal = completedTotal.add(outstanding);
        }

        if (completedCount == 0) {
            throw new IllegalArgumentException("입금 완료 처리할 미수 거래처가 없습니다.");
        }

        paymentRepository.saveAll(newPayments);
        return new BulkCompleteResult(completedCount, money(completedTotal));
    }

    @Transactional(readOnly = true)
    public AutoCompleteSummary getAutoCompleteSummary(YearMonth settlementMonth) {
        List<PaymentEntity> payments = paymentRepository.findForSettlementMonth(
                settlementMonth.toString()
        );

        long count = 0;
        BigDecimal total = ZERO;
        for (PaymentEntity payment : payments) {
            if (!isAutoCompletionPayment(payment)) {
                continue;
            }
            count++;
            total = total.add(safe(payment.getAmount()));
        }

        return new AutoCompleteSummary(count, money(total));
    }

    @Transactional
    public BulkDeleteResult deleteAllAutoCompletionPayments(YearMonth settlementMonth) {
        vendorRepository.lockAllForPaymentUpdate();
        List<PaymentEntity> payments = paymentRepository.findForSettlementMonth(
                settlementMonth.toString()
        );

        List<PaymentEntity> targets = new ArrayList<>();
        BigDecimal total = ZERO;
        for (PaymentEntity payment : payments) {
            if (!isAutoCompletionPayment(payment)) {
                continue;
            }
            targets.add(payment);
            total = total.add(safe(payment.getAmount()));
        }

        if (targets.isEmpty()) {
            throw new IllegalArgumentException("삭제할 자동 입금 완료 기록이 없습니다.");
        }

        paymentRepository.deleteAllInBatch(targets);
        return new BulkDeleteResult(targets.size(), money(total));
    }

    private boolean isAutoCompletionPayment(PaymentEntity payment) {
        if (payment == null || payment.getNote() == null) {
            return false;
        }
        String note = payment.getNote().trim();
        return INDIVIDUAL_COMPLETE_NOTE.equals(note)
                || BULK_COMPLETE_NOTE.equals(note);
    }

    @Transactional
    public void deletePayment(Long paymentId) {
        vendorRepository.lockAllForPaymentUpdate();
        if (!paymentRepository.existsById(paymentId)) {
            throw new IllegalArgumentException("삭제할 입금 기록을 찾을 수 없습니다.");
        }
        paymentRepository.deleteById(paymentId);
    }

    @Transactional(readOnly = true)
    public MonthlyReceivableReport createMonthlyReport(YearMonth month) {
        return createMonthlyReport(
                month,
                monthlySalesReportService.createReport(month)
        );
    }

    @Transactional(readOnly = true)
    public MonthlyReceivableReport createMonthlyReport(
            YearMonth month,
            MonthlySalesReport salesReport
    ) {
        List<PaymentEntity> payments = paymentRepository.findForSettlementMonth(
                month.toString()
        );
        List<VendorEntity> vendors = vendorRepository.findAll();

        Map<String, VendorEntity> vendorByName = new HashMap<>();
        Map<Long, VendorEntity> vendorById = new HashMap<>();
        for (VendorEntity vendor : vendors) {
            vendorByName.put(vendor.getInputName(), vendor);
            vendorById.put(vendor.getId(), vendor);
        }

        Map<Long, PaymentCycle> paymentCycles = new HashMap<>();
        for (VendorProfileEntity profile : vendorProfileRepository.findAllWithVendor()) {
            paymentCycles.put(profile.getVendor().getId(), profile.getPaymentCycle());
        }

        Map<Long, BigDecimal> billedByVendor = new LinkedHashMap<>();
        for (MonthlySalesReport.VendorRow salesRow : salesReport.vendorRows()) {
            VendorEntity vendor = vendorByName.get(salesRow.vendorName());
            if (vendor != null) {
                billedByVendor.put(vendor.getId(), safe(salesRow.confirmedSales()));
            }
        }

        receivableBillingAdjustmentService.correctionsByVendor(month)
                .forEach((vendorId, correction) ->
                        billedByVendor.merge(vendorId, correction, BigDecimal::add)
                );

        Map<Long, BigDecimal> paidByVendor = new LinkedHashMap<>();
        for (PaymentEntity payment : payments) {
            paidByVendor.merge(
                    payment.getVendor().getId(),
                    safe(payment.getAmount()),
                    BigDecimal::add
            );
        }

        List<MonthlyReceivableReport.PaymentRow> weeklyRows = new ArrayList<>();
        for (var payment : weeklyPaymentRepository.findOverlappingMonth(month.atDay(1).minusDays(6), month.atEndOfMonth())) {
            var weekItems = salesItemRepository.findForVendorPeriod(payment.getVendor().getId(),
                    payment.getWeekStart(), payment.getWeekStart().plusDays(6));
            BigDecimal allocated = WeeklyPaymentAllocation.forMonth(payment, month, weekItems);
            if (allocated.signum() == 0) continue;
            paidByVendor.merge(payment.getVendor().getId(), allocated, BigDecimal::add);
            // A null monthly id prevents this row being deleted through the monthly-payment endpoint.
            weeklyRows.add(new MonthlyReceivableReport.PaymentRow(null, payment.getPaymentDate(),
                    payment.getVendor().getId(), payment.getVendor().getInputName(), allocated,
                    "주별 입금 · " + payment.getWeekStart() + " 주 · 월 매출 비율 배분"));
        }

        Map<Long, Boolean> relevantVendorIds = new LinkedHashMap<>();
        billedByVendor.keySet().forEach(id -> relevantVendorIds.put(id, true));
        paidByVendor.keySet().forEach(id -> relevantVendorIds.put(id, true));

        List<MonthlyReceivableReport.VendorRow> vendorRows = new ArrayList<>();
        BigDecimal billedTotal = ZERO;
        BigDecimal paidTotal = ZERO;
        BigDecimal outstandingTotal = ZERO;
        long outstandingVendorCount = 0;

        for (Long vendorId : relevantVendorIds.keySet()) {
            VendorEntity vendor = vendorById.get(vendorId);
            if (vendor == null) {
                continue;
            }

            BigDecimal billed = safe(billedByVendor.get(vendorId));
            BigDecimal paid = safe(paidByVendor.get(vendorId));
            BigDecimal outstanding = billed.subtract(paid);

            billedTotal = billedTotal.add(billed);
            paidTotal = paidTotal.add(paid);
            outstandingTotal = outstandingTotal.add(outstanding);
            if (outstanding.signum() > 0) {
                outstandingVendorCount++;
            }

            vendorRows.add(new MonthlyReceivableReport.VendorRow(
                    vendorId,
                    vendor.getInputName(),
                    paymentCycles.getOrDefault(vendorId, PaymentCycle.MONTHLY),
                    money(billed),
                    money(paid),
                    money(outstanding)
            ));
        }

        vendorRows.sort(
                Comparator.comparing(MonthlyReceivableReport.VendorRow::outstandingAmount)
                        .reversed()
                        .thenComparing(MonthlyReceivableReport.VendorRow::vendorName)
        );

        List<MonthlyReceivableReport.PaymentRow> paymentRows = new ArrayList<>(payments.stream()
                .map(payment -> new MonthlyReceivableReport.PaymentRow(
                        payment.getId(),
                        payment.getPaymentDate(),
                        payment.getVendor().getId(),
                        payment.getVendor().getInputName(),
                        money(payment.getAmount()),
                        payment.getNote()
                ))
                .toList());
        paymentRows.addAll(weeklyRows);
        paymentRows.sort(Comparator.comparing(MonthlyReceivableReport.PaymentRow::paymentDate).reversed());

        return new MonthlyReceivableReport(
                month,
                money(billedTotal),
                money(paidTotal),
                money(outstandingTotal),
                outstandingVendorCount,
                salesReport.missingPriceCount(),
                List.copyOf(vendorRows),
                List.copyOf(paymentRows)
        );
    }

    private void assertMonthlyEntry(Long vendorId) {
        if (vendorProfileRepository.findByVendor_Id(vendorId)
                .map(profile -> profile.getPaymentCycle() == PaymentCycle.WEEKLY).orElse(false)) {
            throw new IllegalArgumentException("주별 거래처의 입금은 주별 입금확인에서 등록해주세요. 월별 집계에는 자동 반영됩니다.");
        }
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return ZERO;
        }
        return value.stripTrailingZeros();
    }

    public record BulkCompleteResult(long vendorCount, BigDecimal totalAmount) {
    }

    public record AutoCompleteSummary(long count, BigDecimal totalAmount) {
    }

    public record BulkDeleteResult(long deletedCount, BigDecimal totalAmount) {
    }
}
