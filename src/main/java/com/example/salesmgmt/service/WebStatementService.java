package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.ItemCatalog;
import com.example.salesmgmt.domain.VendorOption;
import com.example.salesmgmt.domain.WebStatementView;
import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import com.example.salesmgmt.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class WebStatementService {

    private static final String SUNSAN_STATEMENT_NAME = "선산식자재마트";

    private final SalesItemRepository salesItemRepository;
    private final VendorRepository vendorRepository;

    public WebStatementService(
            SalesItemRepository salesItemRepository,
            VendorRepository vendorRepository
    ) {
        this.salesItemRepository = salesItemRepository;
        this.vendorRepository = vendorRepository;
    }

    @Transactional(readOnly = true)
    public List<VendorOption> vendors() {
        return vendorRepository.findAllByOrderByInputNameAsc()
                .stream()
                .map(vendor -> new VendorOption(
                        vendor.getId(),
                        vendor.getInputName(),
                        vendor.getStatementName()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public WebStatementView create(
            YearMonth month,
            Long vendorId
    ) {
        if (vendorId == null) {
            return null;
        }

        var vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("거래처를 찾을 수 없습니다."));

        String statementName = vendor.getStatementName();
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        if (SUNSAN_STATEMENT_NAME.equals(statementName)) {
            start = month.minusMonths(1).atDay(26);
            end = month.atDay(25);
        }

        List<SalesItemEntity> items = salesItemRepository.findForVendorPeriod(
                vendorId,
                start,
                end
        );

        LinkedHashSet<String> orderedItems = new LinkedHashSet<>();
        for (String catalogItem : ItemCatalog.ALL_ITEMS) {
            if (items.stream().anyMatch(item -> catalogItem.equals(item.getItemName()))) {
                orderedItems.add(catalogItem);
            }
        }
        for (SalesItemEntity item : items) {
            orderedItems.add(item.getItemName());
        }
        List<String> itemNames = List.copyOf(orderedItems);

        Map<LocalDate, Map<String, BigDecimal>> quantities = new HashMap<>();
        Map<LocalDate, BigDecimal> amounts = new HashMap<>();

        BigDecimal total = BigDecimal.ZERO;
        long missing = 0;

        for (SalesItemEntity item : items) {
            LocalDate date = item.getSalesOrder().getDeliveryDate();
            quantities.computeIfAbsent(date, ignored -> new HashMap<>())
                    .merge(item.getItemName(), item.getQuantity(), BigDecimal::add);

            if (item.getLineAmount() == null) {
                missing++;
            } else {
                amounts.merge(date, item.getLineAmount(), BigDecimal::add);
                total = total.add(item.getLineAmount());
            }
        }

        List<WebStatementView.DailyRow> dailyRows = new ArrayList<>();
        for (LocalDate date : start.datesUntil(end.plusDays(1)).toList()) {
            Map<String, BigDecimal> byItem = quantities.getOrDefault(date, Map.of());
            List<BigDecimal> qtyList = itemNames.stream()
                    .map(name -> byItem.getOrDefault(name, BigDecimal.ZERO))
                    .toList();

            dailyRows.add(new WebStatementView.DailyRow(
                    date,
                    qtyList,
                    amounts.getOrDefault(date, BigDecimal.ZERO)
            ));
        }

        return new WebStatementView(
                month,
                vendorId,
                statementName == null || statementName.isBlank()
                        ? vendor.getInputName()
                        : statementName,
                total,
                missing,
                itemNames,
                List.copyOf(dailyRows)
        );
    }
}
