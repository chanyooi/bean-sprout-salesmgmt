package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.PaymentCycle;
import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.entity.VendorProfileEntity;
import com.example.salesmgmt.entity.WeeklyPaymentEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import com.example.salesmgmt.repository.VendorProfileRepository;
import com.example.salesmgmt.repository.VendorRepository;
import com.example.salesmgmt.repository.WeeklyPaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WeeklyPaymentService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String COMPLETE_NOTE = "주별 입금 완료 자동 처리";

    private final WeeklyPaymentRepository weeklyPaymentRepository;
    private final VendorRepository vendorRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final SalesItemRepository salesItemRepository;

    public WeeklyPaymentService(
            WeeklyPaymentRepository weeklyPaymentRepository,
            VendorRepository vendorRepository,
            VendorProfileRepository vendorProfileRepository,
            SalesItemRepository salesItemRepository
    ) {
        this.weeklyPaymentRepository = weeklyPaymentRepository;
        this.vendorRepository = vendorRepository;
        this.vendorProfileRepository = vendorProfileRepository;
        this.salesItemRepository = salesItemRepository;
    }

    public LocalDate resolveWeekStart(String requestedDate) {
        LocalDate baseDate = LocalDate.now();
        if (requestedDate != null && !requestedDate.isBlank()) {
            try {
                baseDate = LocalDate.parse(requestedDate);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("조회할 주 날짜 형식이 올바르지 않습니다.");
            }
        }
        return normalizeWeekStart(baseDate);
    }

    @Transactional(readOnly = true)
    public WeeklyReport createReport(LocalDate requestedWeekStart) {
        LocalDate weekStart = normalizeWeekStart(requestedWeekStart);
        LocalDate weekEnd = weekStart.plusDays(6);

        List<VendorProfileEntity> weeklyProfiles = weeklyProfiles();
        Map<Long, VendorEntity> targetByVendorId = new LinkedHashMap<>();
        for (VendorProfileEntity profile : weeklyProfiles) {
            targetByVendorId.put(profile.getVendor().getId(), profile.getVendor());
        }

        Map<Long, BigDecimal[]> dailyByVendor = new LinkedHashMap<>();
        Map<Long, BigDecimal> billedByVendor = new LinkedHashMap<>();
        long missingPriceCount = 0;

        for (SalesItemEntity item : salesItemRepository.findForMonthlyReport(weekStart, weekEnd)) {
            Long vendorId = item.getSalesOrder().getVendor().getId();
            if (!targetByVendorId.containsKey(vendorId)) {
                continue;
            }

            if (item.getLineAmount() == null) {
                missingPriceCount++;
                continue;
            }

            LocalDate date = item.getSalesOrder().getDeliveryDate();
            int dayIndex = date.getDayOfWeek().getValue() - 1;
            BigDecimal amount = safe(item.getLineAmount());

            BigDecimal[] daily = dailyByVendor.computeIfAbsent(vendorId, ignored -> zeroWeek());
            daily[dayIndex] = daily[dayIndex].add(amount);
            billedByVendor.merge(vendorId, amount, BigDecimal::add);
        }

        List<WeeklyPaymentEntity> payments = weeklyPaymentRepository.findForWeek(weekStart);
        Map<Long, BigDecimal> paidByVendor = new LinkedHashMap<>();
        for (WeeklyPaymentEntity payment : payments) {
            paidByVendor.merge(
                    payment.getVendor().getId(),
                    safe(payment.getAmount()),
                    BigDecimal::add
            );
        }

        List<VendorRow> rows = new ArrayList<>();
        BigDecimal billedTotal = ZERO;
        BigDecimal paidTotal = ZERO;
        BigDecimal outstandingTotal = ZERO;
        long outstandingVendorCount = 0;

        for (VendorProfileEntity profile : weeklyProfiles) {
            VendorEntity vendor = profile.getVendor();
            Long vendorId = vendor.getId();
            BigDecimal[] daily = dailyByVendor.getOrDefault(vendorId, zeroWeek());
            BigDecimal billed = safe(billedByVendor.get(vendorId));
            BigDecimal paid = safe(paidByVendor.get(vendorId));
            BigDecimal outstanding = billed.subtract(paid);

            billedTotal = billedTotal.add(billed);
            paidTotal = paidTotal.add(paid);
            outstandingTotal = outstandingTotal.add(outstanding);
            if (outstanding.signum() > 0) {
                outstandingVendorCount++;
            }

            rows.add(new VendorRow(
                    vendorId,
                    vendor.getInputName(),
                    money(daily[0]),
                    money(daily[1]),
                    money(daily[2]),
                    money(daily[3]),
                    money(daily[4]),
                    money(daily[5]),
                    money(daily[6]),
                    money(billed),
                    money(paid),
                    money(outstanding),
                    statusFor(billed, paid, outstanding)
            ));
        }

        List<PaymentRow> paymentRows = payments.stream()
                .map(payment -> new PaymentRow(
                        payment.getId(),
                        payment.getPaymentDate(),
                        payment.getVendor().getInputName(),
                        money(payment.getAmount()),
                        payment.getNote()
                ))
                .toList();

        return new WeeklyReport(
                weekStart,
                weekEnd,
                money(billedTotal),
                money(paidTotal),
                money(outstandingTotal),
                outstandingVendorCount,
                missingPriceCount,
                List.copyOf(rows),
                List.copyOf(paymentRows),
                List.of()
        );
    }

    @Transactional
    public void addPayment(
            LocalDate requestedWeekStart,
            Long vendorId,
            LocalDate paymentDate,
            BigDecimal amount,
            String note
    ) {
        LocalDate weekStart = normalizeWeekStart(requestedWeekStart);
        VendorEntity vendor = targetVendorById(vendorId);
        weeklyPaymentRepository.save(new WeeklyPaymentEntity(
                vendor,
                weekStart,
                paymentDate == null ? LocalDate.now() : paymentDate,
                amount,
                note
        ));
    }

    @Transactional
    public BigDecimal completeOutstanding(
            LocalDate requestedWeekStart,
            Long vendorId,
            LocalDate paymentDate
    ) {
        LocalDate weekStart = normalizeWeekStart(requestedWeekStart);
        VendorEntity vendor = targetVendorById(vendorId);
        WeeklyReport report = createReport(weekStart);
        VendorRow row = report.vendorRows().stream()
                .filter(candidate -> vendorId.equals(candidate.vendorId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("주별 정산 대상 거래처가 아닙니다."));

        if (row.outstandingAmount().signum() <= 0) {
            throw new IllegalArgumentException("이미 입금 완료된 거래처입니다.");
        }

        weeklyPaymentRepository.save(new WeeklyPaymentEntity(
                vendor,
                weekStart,
                paymentDate == null ? LocalDate.now() : paymentDate,
                row.outstandingAmount(),
                COMPLETE_NOTE
        ));
        return money(row.outstandingAmount());
    }

    @Transactional
    public void deletePayment(Long paymentId) {
        if (!weeklyPaymentRepository.existsById(paymentId)) {
            throw new IllegalArgumentException("삭제할 주별 입금 기록을 찾을 수 없습니다.");
        }
        weeklyPaymentRepository.deleteById(paymentId);
    }

    private VendorEntity targetVendorById(Long vendorId) {
        if (vendorId == null) {
            throw new IllegalArgumentException("거래처가 필요합니다.");
        }

        VendorEntity vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("거래처를 찾을 수 없습니다."));

        VendorProfileEntity profile = vendorProfileRepository.findByVendor_Id(vendorId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "거래처 관리에서 입금주기를 먼저 설정해주세요."
                ));

        if (!profile.isActive() || profile.getPaymentCycle() != PaymentCycle.WEEKLY) {
            throw new IllegalArgumentException("주별 입금확인 대상 거래처가 아닙니다.");
        }
        return vendor;
    }

    private List<VendorProfileEntity> weeklyProfiles() {
        return vendorProfileRepository.findAllWithVendor()
                .stream()
                .filter(VendorProfileEntity::isActive)
                .filter(profile -> profile.getPaymentCycle() == PaymentCycle.WEEKLY)
                .sorted(Comparator.comparing(
                        profile -> profile.getVendor().getInputName(),
                        String.CASE_INSENSITIVE_ORDER
                ))
                .toList();
    }

    private LocalDate normalizeWeekStart(LocalDate date) {
        LocalDate base = date == null ? LocalDate.now() : date;
        return base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private BigDecimal[] zeroWeek() {
        return new BigDecimal[] { ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO };
    }

    private String statusFor(BigDecimal billed, BigDecimal paid, BigDecimal outstanding) {
        if (billed.signum() == 0 && paid.signum() == 0) {
            return "사용없음";
        }
        if (outstanding.signum() <= 0) {
            return "입금완료";
        }
        if (paid.signum() > 0) {
            return "일부입금";
        }
        return "미입금";
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

    public record WeeklyReport(
            LocalDate weekStart,
            LocalDate weekEnd,
            BigDecimal billedAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            long outstandingVendorCount,
            long missingPriceCount,
            List<VendorRow> vendorRows,
            List<PaymentRow> paymentRows,
            List<String> missingTargetNames
    ) {}

    public record VendorRow(
            Long vendorId,
            String vendorName,
            BigDecimal mondayAmount,
            BigDecimal tuesdayAmount,
            BigDecimal wednesdayAmount,
            BigDecimal thursdayAmount,
            BigDecimal fridayAmount,
            BigDecimal saturdayAmount,
            BigDecimal sundayAmount,
            BigDecimal billedAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            String status
    ) {}

    public record PaymentRow(
            Long paymentId,
            LocalDate paymentDate,
            String vendorName,
            BigDecimal amount,
            String note
    ) {}
}
