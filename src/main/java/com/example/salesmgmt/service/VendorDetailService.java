package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.VendorHistoricalMonthlySpendEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import com.example.salesmgmt.repository.VendorHistoricalMonthlySpendRepository;
import com.example.salesmgmt.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VendorDetailService {

    public static final YearMonth SYSTEM_START_MONTH = YearMonth.of(2026, 7);

    private final VendorRepository vendorRepository;
    private final SalesItemRepository salesItemRepository;
    private final VendorHistoricalMonthlySpendRepository historicalSpendRepository;

    public VendorDetailService(
            VendorRepository vendorRepository,
            SalesItemRepository salesItemRepository,
            VendorHistoricalMonthlySpendRepository historicalSpendRepository
    ) {
        this.vendorRepository = vendorRepository;
        this.salesItemRepository = salesItemRepository;
        this.historicalSpendRepository = historicalSpendRepository;
    }

    @Transactional(readOnly = true)
    public VendorDetailData load(Long vendorId, YearMonth selectedMonth) {
        var vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "거래처를 찾을 수 없습니다."
                ));

        int selectedYear = selectedMonth.getYear();
        LocalDate yearStart = LocalDate.of(selectedYear, 1, 1);
        LocalDate yearEnd = LocalDate.of(selectedYear, 12, 31);

        List<SalesItemEntity> items = salesItemRepository.findForVendorPeriod(
                vendorId,
                yearStart,
                yearEnd
        );

        Map<YearMonth, BigDecimal> totals = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) {
            totals.put(YearMonth.of(selectedYear, month), BigDecimal.ZERO);
        }

        for (SalesItemEntity item : items) {
            YearMonth itemMonth = YearMonth.from(
                    item.getSalesOrder().getDeliveryDate()
            );
            if (item.getLineAmount() != null) {
                totals.computeIfPresent(
                        itemMonth,
                        (month, total) -> total.add(item.getLineAmount())
                );
            }
        }

        List<VendorHistoricalMonthlySpendEntity> historical =
                historicalSpendRepository.findAllByVendor_IdAndSpendMonthBetween(
                        vendorId,
                        yearStart,
                        yearEnd
                );

        Map<YearMonth, BigDecimal> manualAmounts = new LinkedHashMap<>();
        for (VendorHistoricalMonthlySpendEntity row : historical) {
            YearMonth month = YearMonth.from(row.getSpendMonth());
            manualAmounts.put(month, row.getAmount());
            if (month.isBefore(SYSTEM_START_MONTH)) {
                totals.put(month, row.getAmount());
            }
        }

        List<OrderItemRow> selectedRows = new ArrayList<>();
        for (SalesItemEntity item : items) {
            YearMonth itemMonth = YearMonth.from(
                    item.getSalesOrder().getDeliveryDate()
            );
            if (!itemMonth.equals(selectedMonth)) {
                continue;
            }

            BigDecimal editablePrice = item.getUnitPrice();
            if (editablePrice != null && "회수통".equals(item.getItemName())) {
                editablePrice = editablePrice.abs();
            }

            selectedRows.add(new OrderItemRow(
                    item.getId(),
                    item.getSalesOrder().getId(),
                    item.getSalesOrder().getOrderNumber(),
                    item.getSalesOrder().getDeliveryDate(),
                    item.getItemName(),
                    item.getQuantity(),
                    editablePrice,
                    item.getLineAmount(),
                    item.getSalesOrder().getDeliveryMethod(),
                    item.getSalesOrder().getNote()
            ));
        }

        List<MonthlySpendRow> monthlyRows = totals.entrySet().stream()
                .map(entry -> new MonthlySpendRow(
                        entry.getKey().toString(),
                        entry.getKey().getMonthValue(),
                        entry.getValue(),
                        manualAmounts.containsKey(entry.getKey()),
                        entry.getKey().isBefore(SYSTEM_START_MONTH)
                ))
                .toList();

        BigDecimal selectedTotal = selectedRows.stream()
                .map(OrderItemRow::lineAmount)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (selectedMonth.isBefore(SYSTEM_START_MONTH)) {
            selectedTotal = totals.getOrDefault(selectedMonth, BigDecimal.ZERO);
        }

        BigDecimal yearTotal = monthlyRows.stream()
                .map(MonthlySpendRow::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long selectedOrderCount = selectedRows.stream()
                .map(OrderItemRow::orderId)
                .distinct()
                .count();

        return new VendorDetailData(
                vendor.getId(),
                vendor.getInputName(),
                vendor.getStatementName(),
                selectedMonth.toString(),
                selectedYear,
                monthlyRows,
                List.copyOf(selectedRows),
                selectedTotal,
                yearTotal,
                selectedOrderCount,
                selectedMonth.isBefore(SYSTEM_START_MONTH)
        );
    }

    @Transactional
    public void saveHistoricalMonthlySpend(
            Long vendorId,
            YearMonth month,
            BigDecimal amount
    ) {
        if (month == null) {
            throw new IllegalArgumentException("등록할 월을 선택해주세요.");
        }
        if (!month.isBefore(SYSTEM_START_MONTH)) {
            throw new IllegalArgumentException(
                    "2026년 7월부터는 업로드된 장부 금액을 사용합니다. 2026년 6월 이전만 직접 등록할 수 있습니다."
            );
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("사용금액은 0원 이상으로 입력해주세요.");
        }

        var vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "거래처를 찾을 수 없습니다."
                ));

        LocalDate spendMonth = month.atDay(1);
        VendorHistoricalMonthlySpendEntity entity = historicalSpendRepository
                .findByVendor_IdAndSpendMonth(vendorId, spendMonth)
                .orElseGet(() -> new VendorHistoricalMonthlySpendEntity(
                        vendor,
                        spendMonth,
                        amount
                ));

        entity.updateAmount(amount);
        historicalSpendRepository.save(entity);
    }

    public record VendorDetailData(
            Long vendorId,
            String vendorName,
            String statementName,
            String selectedMonth,
            int selectedYear,
            List<MonthlySpendRow> monthlySpend,
            List<OrderItemRow> orderItems,
            BigDecimal selectedTotal,
            BigDecimal yearTotal,
            long selectedOrderCount,
            boolean historicalMonth
    ) {}

    public record MonthlySpendRow(
            String month,
            int monthNumber,
            BigDecimal amount,
            boolean manuallyRegistered,
            boolean historicalMonth
    ) {}

    public record OrderItemRow(
            Long itemId,
            Long orderId,
            String orderNumber,
            LocalDate deliveryDate,
            String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineAmount,
            String deliveryMethod,
            String note
    ) {}
}
