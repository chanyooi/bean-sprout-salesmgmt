package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.PaymentCycle;
import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.entity.VendorProfileEntity;
import com.example.salesmgmt.entity.WeeklyPaymentEntity;
import com.example.salesmgmt.entity.WeeklySettlementPeriodEntity;
import com.example.salesmgmt.repository.WeeklySettlementPeriodRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class WeeklyPaymentService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String COMPLETE_NOTE = "주별 입금 완료 자동 처리";

    private final WeeklyPaymentRepository weeklyPaymentRepository;
    private final VendorRepository vendorRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final SalesItemRepository salesItemRepository;
    private final WeeklySettlementPeriodRepository periods;

    public WeeklyPaymentService(
            WeeklyPaymentRepository weeklyPaymentRepository,
            VendorRepository vendorRepository,
            VendorProfileRepository vendorProfileRepository,
            SalesItemRepository salesItemRepository,
            WeeklySettlementPeriodRepository periods
    ) {
        this.weeklyPaymentRepository = weeklyPaymentRepository;
        this.vendorRepository = vendorRepository;
        this.vendorProfileRepository = vendorProfileRepository;
        this.salesItemRepository = salesItemRepository;
        this.periods = periods;
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

        List<WeeklySettlementPeriodEntity> relevant = periods.findForCalendarWeek(weekStart, weekEnd);
        Map<Long, List<WeeklySettlementPeriodEntity>> periodsByVendor = new LinkedHashMap<>();
        for (var period : relevant) {
            periodsByVendor.computeIfAbsent(period.getVendor().getId(), ignored -> new ArrayList<>()).add(period);
        }
        LocalDate queryStart = weekStart;
        LocalDate queryEnd = weekEnd;
        for (var period : relevant) {
            if (!period.getWeekAnchor().equals(weekStart)) continue;
            if (period.getStartsOn().isBefore(queryStart)) queryStart = period.getStartsOn();
            if (period.getEndsOn().isAfter(queryEnd)) queryEnd = period.getEndsOn();
        }
        Map<Long, List<SalesItemEntity>> salesByVendor = new LinkedHashMap<>();
        for (var item : salesItemRepository.findForMonthlyReport(queryStart, queryEnd)) {
            Long id = item.getSalesOrder().getVendor().getId();
            if (targetByVendorId.containsKey(id)) {
                salesByVendor.computeIfAbsent(id, ignored -> new ArrayList<>()).add(item);
            }
        }
        long missingPriceCount = 0;
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
            var vendorPeriods = periodsByVendor.getOrDefault(vendorId, List.of());
            var custom = vendorPeriods.stream().filter(period -> period.getWeekAnchor().equals(weekStart))
                    .findFirst().orElse(null);
            LocalDate start = custom == null ? weekStart : custom.getStartsOn();
            LocalDate end = custom == null ? weekEnd : custom.getEndsOn();
            var daily = new TreeMap<LocalDate, BigDecimal>();
            var unassigned = new TreeMap<LocalDate, BigDecimal>();
            for (var item : salesByVendor.getOrDefault(vendorId, List.of())) {
                LocalDate date = item.getSalesOrder().getDeliveryDate();
                boolean assigned = custom != null ? contains(start, end, date)
                        : contains(start, end, date) && vendorPeriods.stream().noneMatch(period ->
                            contains(period.getStartsOn(), period.getEndsOn(), date));
                BigDecimal amount = ReceivableBillingAdjustmentService.billingAmount(item);
                if (assigned && item.getLineAmount() == null && amount.signum() == 0) missingPriceCount++;
                if (assigned) daily.merge(date, amount, BigDecimal::add);
                if (!assigned && custom != null && contains(weekStart, weekEnd, date)
                        && vendorPeriods.stream().noneMatch(period -> contains(period.getStartsOn(), period.getEndsOn(), date))) {
                    unassigned.merge(date, amount, BigDecimal::add);
                }
            }
            BigDecimal billed = sum(daily);
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
                    start,
                    end,
                    custom != null,
                    dailyRows(daily),
                    dailyRows(unassigned),
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

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void addPayment(
            LocalDate requestedWeekStart,
            Long vendorId,
            LocalDate paymentDate,
            BigDecimal amount,
            String note
    ) {
        LocalDate weekStart = normalizeWeekStart(requestedWeekStart);
        VendorEntity vendor = targetVendorById(vendorId);
        if (periods.findByVendor_IdAndWeekAnchor(vendorId, weekStart).isEmpty()) {
            LocalDate weekEnd = weekStart.plusDays(6);
            var covering = periods.findByVendor_IdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqual(vendorId, weekEnd, weekStart);
            if (weekStart.datesUntil(weekEnd.plusDays(1)).allMatch(day -> covering.stream().anyMatch(period ->
                    contains(period.getStartsOn(), period.getEndsOn(), day)))) {
                throw new IllegalArgumentException("이 주의 날짜는 다른 정산 회차에 포함되어 있습니다. 해당 회차를 선택해 주세요.");
            }
        }
        LocalDate actualPaymentDate = paymentDate == null ? LocalDate.now() : paymentDate;
        assertNoDuplicateWeeklyPayment(
                weekStart,
                vendorId,
                actualPaymentDate,
                amount,
                note
        );
        weeklyPaymentRepository.save(new WeeklyPaymentEntity(
                vendor,
                weekStart,
                actualPaymentDate,
                amount,
                note
        ));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
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

        LocalDate automaticPaymentDate = paymentDate == null
                ? WeeklyPaymentDateRepairService.settlementDateForWeek(weekStart, LocalDate.now())
                : paymentDate;

        weeklyPaymentRepository.save(new WeeklyPaymentEntity(
                vendor,
                weekStart,
                automaticPaymentDate,
                row.outstandingAmount(),
                COMPLETE_NOTE
        ));
        return money(row.outstandingAmount());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deletePayment(Long paymentId) {
        var payment = weeklyPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("삭제할 주별 입금 기록을 찾을 수 없습니다."));
        vendorRepository.findByIdForUpdate(payment.getVendor().getId());
        weeklyPaymentRepository.deleteById(paymentId);
    }

    private VendorEntity targetVendorById(Long vendorId) {
        if (vendorId == null) {
            throw new IllegalArgumentException("거래처가 필요합니다.");
        }

        VendorEntity vendor = vendorRepository.findByIdForUpdate(vendorId)
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

    private void assertNoDuplicateWeeklyPayment(
            LocalDate weekStart,
            Long vendorId,
            LocalDate paymentDate,
            BigDecimal amount,
            String note
    ) {
        String normalizedNote = normalizeNote(note);
        boolean duplicate = weeklyPaymentRepository.findForWeek(weekStart)
                .stream()
                .anyMatch(payment ->
                        vendorId.equals(payment.getVendor().getId())
                                && paymentDate.equals(payment.getPaymentDate())
                                && amount.compareTo(payment.getAmount()) == 0
                                && java.util.Objects.equals(
                                        normalizedNote,
                                        normalizeNote(payment.getNote())
                                )
                );

        if (duplicate) {
            throw new IllegalArgumentException(
                    "같은 거래처·입금일·금액·메모의 주별 입금 기록이 이미 있습니다."
            );
        }
    }

    private String normalizeNote(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
        return base.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }

    private static boolean contains(LocalDate start, LocalDate end, LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    private BigDecimal sum(Map<LocalDate, BigDecimal> daily) {
        return daily.values().stream().reduce(ZERO, BigDecimal::add);
    }

    private List<DailyAmount> dailyRows(Map<LocalDate, BigDecimal> daily) {
        return daily.entrySet().stream().map(entry -> new DailyAmount(entry.getKey(), money(entry.getValue()))).toList();
    }

    @Transactional(readOnly = true)
    public PeriodPreview previewPeriod(Long vendorId, LocalDate requestedWeek, LocalDate start, LocalDate end) {
        var vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("거래처를 찾을 수 없습니다."));
        return preparePeriod(vendor, normalizeWeekStart(requestedWeek), start, end);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void savePeriod(Long vendorId, LocalDate requestedWeek, LocalDate start, LocalDate end, String confirmation) {
        LocalDate anchor = normalizeWeekStart(requestedWeek);
        var vendor = targetVendorById(vendorId);
        var preview = preparePeriod(vendor, anchor, start, end);
        if (!preview.confirmation().equals(confirmation)) {
            throw new IllegalArgumentException("정산 내역이 변경되었습니다. 변경 결과를 다시 확인해 주세요.");
        }
        var existing = periods.findByVendor_IdAndWeekAnchor(vendorId, anchor);
        if (existing.isPresent()) existing.get().changeDates(start, end);
        else periods.save(new WeeklySettlementPeriodEntity(vendor, anchor, start, end));
    }

    private PeriodPreview preparePeriod(VendorEntity vendor, LocalDate anchor, LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start) || end.isAfter(start.plusDays(365))) {
            throw new IllegalArgumentException("시작일과 종료일을 확인해 주세요. 한 회차는 최대 366일입니다.");
        }
        if (end.isBefore(anchor) || start.isAfter(anchor.plusDays(6))) {
            throw new IllegalArgumentException("정산 기간에 달력에서 선택한 주의 날짜가 포함되어야 합니다.");
        }
        var overlaps = periods.findByVendor_IdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqual(vendor.getId(), end, start);
        if (overlaps.stream().anyMatch(period -> !period.getWeekAnchor().equals(anchor))) {
            throw new IllegalArgumentException("이 거래처의 다른 정산 기간과 겹칩니다.");
        }
        var existing = periods.findByVendor_IdAndWeekAnchor(vendor.getId(), anchor);
        var attached = weeklyPaymentRepository.findForWeek(anchor).stream()
                .filter(payment -> payment.getVendor().getId().equals(vendor.getId())).toList();
        if (existing.isEmpty() && !attached.isEmpty() && (!start.equals(anchor) || !end.equals(anchor.plusDays(6)))) {
            throw new IllegalArgumentException("이미 입금이 기록된 기존 회차의 기간은 그대로 유지됩니다. 입금 기록이 없는 회차를 선택해 주세요.");
        }
        if (weeklyPaymentRepository.findOverlappingWeeks(start.minusDays(6), end).stream()
                .anyMatch(payment -> payment.getVendor().getId().equals(vendor.getId())
                        && !payment.getWeekStart().equals(anchor)
                        && periods.findByVendor_IdAndWeekAnchor(vendor.getId(), payment.getWeekStart()).isEmpty())) {
            throw new IllegalArgumentException("이미 입금이 기록된 다른 기존 회차와 겹칩니다.");
        }
        var previous = createReport(anchor).vendorRows().stream().filter(row -> row.vendorId().equals(vendor.getId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("주별 정산 대상 거래처가 아닙니다."));
        var sales = salesItemRepository.findForVendorPeriod(vendor.getId(), start, end);
        BigDecimal billed = money(sales.stream().map(ReceivableBillingAdjustmentService::billingAmount)
                .reduce(ZERO, BigDecimal::add));
        String snapshot = previous + "|" + start + "|" + end + "|" + billed + "|"
                + attached.stream().map(payment -> payment.getId() + ":" + payment.getAmount()).toList()
                + "|" + sales.stream().map(item -> item.getId() + ":" + item.getSalesOrder().getDeliveryDate()
                        + ":" + ReceivableBillingAdjustmentService.billingAmount(item)).sorted().toList();
        try {
            String token = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(snapshot.getBytes(StandardCharsets.UTF_8)));
            return new PeriodPreview(previous, start, end, billed, money(billed.subtract(previous.paidAmount())), token);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
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
            LocalDate periodStart,
            LocalDate periodEnd,
            boolean customPeriod,
            List<DailyAmount> dailyAmounts,
            List<DailyAmount> unassignedAmounts,
            BigDecimal billedAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            String status
    ) {}

    public record DailyAmount(LocalDate date, BigDecimal amount) {}

    public record PeriodPreview(VendorRow previous, LocalDate start, LocalDate end,
                                BigDecimal billed, BigDecimal outstanding, String confirmation) {}

    public record PaymentRow(
            Long paymentId,
            LocalDate paymentDate,
            String vendorName,
            BigDecimal amount,
            String note
    ) {}
}
