package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
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

    private final VendorRepository vendorRepository;
    private final SalesItemRepository salesItemRepository;

    public VendorDetailService(
            VendorRepository vendorRepository,
            SalesItemRepository salesItemRepository
    ) {
        this.vendorRepository = vendorRepository;
        this.salesItemRepository = salesItemRepository;
    }

    @Transactional(readOnly = true)
    public VendorDetailData load(Long vendorId, YearMonth selectedMonth) {
        var vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "거래처를 찾을 수 없습니다."
                ));

        YearMonth currentMonth = YearMonth.now();
        YearMonth summaryStartMonth = currentMonth.minusMonths(11);
        YearMonth queryStartMonth = selectedMonth.isBefore(summaryStartMonth)
                ? selectedMonth
                : summaryStartMonth;
        YearMonth queryEndMonth = selectedMonth.isAfter(currentMonth)
                ? selectedMonth
                : currentMonth;

        List<SalesItemEntity> items = salesItemRepository.findForVendorPeriod(
                vendorId,
                queryStartMonth.atDay(1),
                queryEndMonth.atEndOfMonth()
        );

        Map<YearMonth, BigDecimal> totals = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            totals.put(currentMonth.minusMonths(i), BigDecimal.ZERO);
        }

        List<OrderItemRow> selectedRows = new ArrayList<>();
        for (SalesItemEntity item : items) {
            YearMonth itemMonth = YearMonth.from(
                    item.getSalesOrder().getDeliveryDate()
            );

            if (totals.containsKey(itemMonth) && item.getLineAmount() != null) {
                totals.computeIfPresent(
                        itemMonth,
                        (month, total) -> total.add(item.getLineAmount())
                );
            }

            if (itemMonth.equals(selectedMonth)) {
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
        }

        List<MonthlySpendRow> monthlyRows = totals.entrySet().stream()
                .map(entry -> new MonthlySpendRow(
                        entry.getKey().toString(),
                        entry.getValue()
                ))
                .toList();

        BigDecimal selectedTotal = selectedRows.stream()
                .map(OrderItemRow::lineAmount)
                .filter(value -> value != null)
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
                monthlyRows,
                List.copyOf(selectedRows),
                selectedTotal,
                selectedOrderCount
        );
    }

    public record VendorDetailData(
            Long vendorId,
            String vendorName,
            String statementName,
            String selectedMonth,
            List<MonthlySpendRow> monthlySpend,
            List<OrderItemRow> orderItems,
            BigDecimal selectedTotal,
            long selectedOrderCount
    ) {}

    public record MonthlySpendRow(
            String month,
            BigDecimal amount
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
