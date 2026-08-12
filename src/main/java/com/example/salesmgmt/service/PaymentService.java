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

    private final PaymentRepository paymentRepository;
    private final VendorRepository vendorRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final MonthlySalesReportService monthlySalesReportService;

    public PaymentService(
            PaymentRepository paymentRepository,
            VendorRepository vendorRepository,
            VendorProfileRepository vendorProfileRepository,
            MonthlySalesReportService monthlySalesReportService
    ) {
        this.paymentRepository = paymentRepository;
        this.vendorRepository = vendorRepository;
        this.vendorProfileRepository = vendorProfileRepository;
        this.monthlySalesReportService = monthlySalesReportService;
    }

    @Transactional(readOnly = true)
    public YearMonth resolveMonth(String requestedMonth) {
        if (requestedMonth != null && !requestedMonth.isBlank()) {
            try {
                return YearMonth.parse(requestedMonth);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(
                        "정산월 형식이 올바르지 않습니다."
                );
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
        VendorEntity vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "거래처를 찾을 수 없습니다."
                ));

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
        VendorEntity vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "거래처를 찾을 수 없습니다."
                ));

        MonthlyReceivableReport report =
                createMonthlyReport(settlementMonth);

        MonthlyReceivableReport.VendorRow targetRow =
                report.vendorRows()
                        .stream()
                        .filter(row -> vendorId.equals(row.vendorId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "해당 월의 청구 또는 입금 내역이 없습니다."
                        ));

        BigDecimal outstanding =
                safe(targetRow.outstandingAmount());

        if (outstanding.signum() <= 0) {
            throw new IllegalArgumentException(
                    "이미 입금 완료된 거래처입니다."
            );
        }

        paymentRepository.save(new PaymentEntity(
                vendor,
                settlementMonth.toString(),
                paymentDate == null ? LocalDate.now() : paymentDate,
                outstanding,
                "입금 완료 자동 처리"
        ));

        return money(outstanding);
    }

    @Transactional
    public void deletePayment(Long paymentId) {
        if (!paymentRepository.existsById(paymentId)) {
            throw new IllegalArgumentException(
                    "삭제할 입금 기록을 찾을 수 없습니다."
            );
        }

        paymentRepository.deleteById(paymentId);
    }

    @Transactional(readOnly = true)
    public MonthlyReceivableReport createMonthlyReport(
            YearMonth month
    ) {
        MonthlySalesReport salesReport =
                monthlySalesReportService.createReport(month);

        List<PaymentEntity> payments =
                paymentRepository.findForSettlementMonth(
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

        for (VendorProfileEntity profile
                : vendorProfileRepository.findAll()) {

            paymentCycles.put(
                    profile.getVendor().getId(),
                    profile.getPaymentCycle()
            );
        }

        Map<Long, BigDecimal> billedByVendor =
                new LinkedHashMap<>();

        for (MonthlySalesReport.VendorRow salesRow
                : salesReport.vendorRows()) {

            VendorEntity vendor =
                    vendorByName.get(
                            salesRow.vendorName()
                    );

            if (vendor != null) {
                billedByVendor.put(
                        vendor.getId(),
                        safe(salesRow.confirmedSales())
                );
            }
        }

        Map<Long, BigDecimal> paidByVendor =
                new LinkedHashMap<>();

        for (PaymentEntity payment : payments) {
            paidByVendor.merge(
                    payment.getVendor().getId(),
                    payment.getAmount(),
                    BigDecimal::add
            );
        }

        Map<Long, Boolean> relevantVendorIds =
                new LinkedHashMap<>();

        billedByVendor.keySet()
                .forEach(id -> relevantVendorIds.put(id, true));

        paidByVendor.keySet()
                .forEach(id -> relevantVendorIds.put(id, true));

        List<MonthlyReceivableReport.VendorRow> vendorRows =
                new ArrayList<>();

        BigDecimal billedTotal = ZERO;
        BigDecimal paidTotal = ZERO;
        BigDecimal outstandingTotal = ZERO;
        long outstandingVendorCount = 0;

        for (Long vendorId : relevantVendorIds.keySet()) {
            VendorEntity vendor =
                    vendorById.get(vendorId);

            if (vendor == null) {
                continue;
            }

            BigDecimal billed =
                    safe(billedByVendor.get(vendorId));

            BigDecimal paid =
                    safe(paidByVendor.get(vendorId));

            BigDecimal outstanding =
                    billed.subtract(paid);

            billedTotal = billedTotal.add(billed);
            paidTotal = paidTotal.add(paid);
            outstandingTotal =
                    outstandingTotal.add(outstanding);

            if (outstanding.signum() > 0) {
                outstandingVendorCount++;
            }

            vendorRows.add(
                    new MonthlyReceivableReport.VendorRow(
                            vendorId,
                            vendor.getInputName(),
                            paymentCycles.getOrDefault(
                                    vendorId,
                                    PaymentCycle.MONTHLY
                            ),
                            money(billed),
                            money(paid),
                            money(outstanding)
                    )
            );
        }

        vendorRows.sort(
                Comparator
                        .comparing(
                                MonthlyReceivableReport.VendorRow
                                        ::outstandingAmount
                        )
                        .reversed()
                        .thenComparing(
                                MonthlyReceivableReport.VendorRow
                                        ::vendorName
                        )
        );

        List<MonthlyReceivableReport.PaymentRow> paymentRows =
                payments.stream()
                        .map(payment ->
                                new MonthlyReceivableReport.PaymentRow(
                                        payment.getId(),
                                        payment.getPaymentDate(),
                                        payment.getVendor().getId(),
                                        payment.getVendor().getInputName(),
                                        money(payment.getAmount()),
                                        payment.getNote()
                                )
                        )
                        .toList();

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

    private BigDecimal safe(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return ZERO;
        }

        return value.stripTrailingZeros();
    }
}
