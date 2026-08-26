package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.entity.WeeklyPaymentEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
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
import java.util.Locale;
import java.util.Map;

@Service
public class WeeklyPaymentService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String COMPLETE_NOTE = "주별 입금 완료 자동 처리";

    private static final List<TargetVendor> TARGETS = List.of(
            new TargetVendor("옥계빅", List.of("옥계빅")),
            new TargetVendor("상모빅", List.of("상모빅")),
            new TargetVendor("고향가마솥", List.of("고향가마솥", "고향가마솥추어탕", "고향추어탕")),
            new TargetVendor("명희네", List.of("명희네", "명희네해장", "명희네해장국")),
            new TargetVendor("플래쉬", List.of("플래쉬", "플래시")),
            new TargetVendor("더킹마트", List.of("더킹마트"))
    );

    private final WeeklyPaymentRepository weeklyPaymentRepository;
    private final VendorRepository vendorRepository;
    private final SalesItemRepository salesItemRepository;

    public WeeklyPaymentService(
            WeeklyPaymentRepository weeklyPaymentRepository,
            VendorRepository vendorRepository,
            SalesItemRepository salesItemRepository
    ) {
        this.weeklyPaymentRepository = weeklyPaymentRepository;
        this.vendorRepository = vendorRepository;
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

        List<VendorEntity> allVendors = vendorRepository.findAllByOrderByInputNameAsc();
        Map<String, VendorEntity> vendorsByNormalizedName = new HashMap<>();
        for (VendorEntity vendor : allVendors) {
            vendorsByNormalizedName.put(normalizeName(vendor.getInputName()), vendor);
        }

        List<ResolvedTarget> resolvedTargets = new ArrayList<>();
        List<String> missingTargets = new ArrayList<>();
        for (int index = 0; index < TARGETS.size(); index++) {
            TargetVendor target = TARGETS.get(index);
            VendorEntity vendor = resolveVendor(target, vendorsByNormalizedName);
            if (vendor == null) {
                missingTargets.add(target.displayName());
            } else {
                resolvedTargets.add(new ResolvedTarget(index, target.displayName(), vendor));
            }
        }

        Map<Long, ResolvedTarget> targetByVendorId = new HashMap<>();
        for (ResolvedTarget target : resolvedTargets) {
            targetByVendorId.put(target.vendor().getId(), target);
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

        resolvedTargets.sort(Comparator.comparingInt(ResolvedTarget::order));

        List<VendorRow> rows = new ArrayList<>();
        BigDecimal billedTotal = ZERO;
        BigDecimal paidTotal = ZERO;
        BigDecimal outstandingTotal = ZERO;
        long outstandingVendorCount = 0;

        for (ResolvedTarget target : resolvedTargets) {
            Long vendorId = target.vendor().getId();
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
                    target.vendor().getInputName(),
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
                List.copyOf(missingTargets)
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
        String normalized = normalizeName(vendor.getInputName());
        boolean target = TARGETS.stream()
                .flatMap(config -> config.aliases().stream())
                .map(this::normalizeName)
                .anyMatch(normalized::equals);
        if (!target) {
            throw new IllegalArgumentException("주별 입금확인 대상 거래처가 아닙니다.");
        }
        return vendor;
    }

    private VendorEntity resolveVendor(
            TargetVendor target,
            Map<String, VendorEntity> vendorsByNormalizedName
    ) {
        for (String alias : target.aliases()) {
            VendorEntity vendor = vendorsByNormalizedName.get(normalizeName(alias));
            if (vendor != null) {
                return vendor;
            }
        }
        return null;
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

    private String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "")
                .trim()
                .toLowerCase(Locale.KOREA);
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

    private record TargetVendor(String displayName, List<String> aliases) {}
    private record ResolvedTarget(int order, String displayName, VendorEntity vendor) {}

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
