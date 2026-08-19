package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.ItemCatalog;
import com.example.salesmgmt.domain.VendorOption;
import com.example.salesmgmt.domain.WebStatementView;
import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import com.example.salesmgmt.repository.VendorPriceRepository;
import com.example.salesmgmt.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class WebStatementService {

    private static final String SUNSAN_STATEMENT_NAME = "선산식자재마트";
    private static final String RETURN_CONTAINER = "회수통";

    private static final List<String> CLASSIC_DAILY_ITEMS = List.of(
            "두절kg",
            "일반콩나물",
            "곱슬콩나물",
            "회수통",
            "3.5kg일반",
            "3.5kg곱슬",
            "숙주"
    );

    private static final List<String> CLASSIC_SUMMARY_ITEMS = List.of(
            "두절kg",
            "일반콩나물",
            "곱슬콩나물",
            "3.5kg일반",
            "3.5kg곱슬",
            "숙주"
    );

    private final SalesItemRepository salesItemRepository;
    private final VendorRepository vendorRepository;
    private final VendorPriceRepository vendorPriceRepository;

    public WebStatementService(
            SalesItemRepository salesItemRepository,
            VendorRepository vendorRepository,
            VendorPriceRepository vendorPriceRepository
    ) {
        this.salesItemRepository = salesItemRepository;
        this.vendorRepository = vendorRepository;
        this.vendorPriceRepository = vendorPriceRepository;
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

        LinkedHashSet<String> orderedItems = new LinkedHashSet<>(CLASSIC_DAILY_ITEMS);
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
        Map<String, BigDecimal> monthlyQuantities = new LinkedHashMap<>();
        Map<String, BigDecimal> monthlyAmounts = new LinkedHashMap<>();

        BigDecimal grossAmount = BigDecimal.ZERO;
        BigDecimal returnContainerAmount = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        long missing = 0;

        for (SalesItemEntity item : items) {
            LocalDate date = item.getSalesOrder().getDeliveryDate();
            String itemName = item.getItemName();

            quantities.computeIfAbsent(date, ignored -> new HashMap<>())
                    .merge(itemName, item.getQuantity(), BigDecimal::add);
            monthlyQuantities.merge(itemName, item.getQuantity(), BigDecimal::add);

            if (item.getLineAmount() == null) {
                missing++;
                continue;
            }

            BigDecimal lineAmount = item.getLineAmount();
            amounts.merge(date, lineAmount, BigDecimal::add);
            monthlyAmounts.merge(itemName, lineAmount, BigDecimal::add);
            total = total.add(lineAmount);

            if (RETURN_CONTAINER.equals(itemName)) {
                returnContainerAmount = returnContainerAmount.add(lineAmount);
            } else {
                grossAmount = grossAmount.add(lineAmount);
            }
        }

        Map<String, BigDecimal> configuredPrices = new HashMap<>();
        vendorPriceRepository.findByVendor_IdOrderByItemNameAsc(vendorId)
                .forEach(price -> configuredPrices.put(price.getItemName(), price.getUnitPrice()));

        List<WebStatementView.ItemSummary> summaries = CLASSIC_SUMMARY_ITEMS.stream()
                .map(itemName -> new WebStatementView.ItemSummary(
                        itemName,
                        monthlyQuantities.getOrDefault(itemName, BigDecimal.ZERO),
                        configuredPrices.get(itemName),
                        monthlyAmounts.getOrDefault(itemName, BigDecimal.ZERO)
                ))
                .toList();

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

        String vendorName = statementName == null || statementName.isBlank()
                ? vendor.getInputName()
                : statementName;

        String deliveryLabel = vendor.getStatementDeliveryMethod() == null
                ? ""
                : vendor.getStatementDeliveryMethod().getLabel();

        return new WebStatementView(
                month,
                vendorId,
                vendorName,
                deliveryLabel,
                grossAmount,
                returnContainerAmount,
                total,
                missing,
                itemNames,
                List.copyOf(summaries),
                List.copyOf(dailyRows)
        );
    }
}
