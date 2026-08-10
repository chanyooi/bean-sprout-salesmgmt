package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.EditableSaleRow;
import com.example.salesmgmt.domain.WebStatementView;
import com.example.salesmgmt.domain.VendorOption;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class WebStatementService {

    private final SalesManagementService salesManagementService;

    public WebStatementService(
            SalesManagementService salesManagementService
    ) {
        this.salesManagementService = salesManagementService;
    }

    public List<VendorOption> vendors() {
        return salesManagementService.findVendorOptions();
    }

    public WebStatementView create(
            YearMonth month,
            Long vendorId
    ) {
        if (vendorId == null) {
            return null;
        }

        List<EditableSaleRow> rows =
                salesManagementService.findRows(
                        month,
                        vendorId
                );

        String vendorName = rows.isEmpty()
                ? vendors().stream()
                    .filter(v -> v.id().equals(vendorId))
                    .map(VendorOption::inputName)
                    .findFirst()
                    .orElse("거래처")
                : rows.getFirst().inputVendor();

        List<String> itemNames = rows.stream()
                .map(EditableSaleRow::item)
                .distinct()
                .sorted()
                .toList();

        Map<LocalDate, Map<String, BigDecimal>> quantities =
                new TreeMap<>();
        Map<LocalDate, BigDecimal> amounts =
                new TreeMap<>();

        BigDecimal total = BigDecimal.ZERO;
        long missing = 0;

        for (EditableSaleRow row : rows) {
            quantities.computeIfAbsent(
                    row.deliveryDate(),
                    ignored -> new HashMap<>()
            ).merge(
                    row.item(),
                    row.quantity(),
                    BigDecimal::add
            );

            if (row.lineAmount() == null) {
                missing++;
            } else {
                amounts.merge(
                        row.deliveryDate(),
                        row.lineAmount(),
                        BigDecimal::add
                );
                total = total.add(row.lineAmount());
            }
        }

        List<WebStatementView.DailyRow> dailyRows =
                new ArrayList<>();

        Set<LocalDate> dates = new TreeSet<>();
        dates.addAll(quantities.keySet());
        dates.addAll(amounts.keySet());

        for (LocalDate date : dates) {
            Map<String, BigDecimal> byItem =
                    quantities.getOrDefault(
                            date,
                            Map.of()
                    );

            List<BigDecimal> qtyList =
                    itemNames.stream()
                            .map(name -> byItem.getOrDefault(
                                    name,
                                    BigDecimal.ZERO
                            ))
                            .toList();

            dailyRows.add(
                    new WebStatementView.DailyRow(
                            date,
                            qtyList,
                            amounts.getOrDefault(
                                    date,
                                    BigDecimal.ZERO
                            )
                    )
            );
        }

        return new WebStatementView(
                month,
                vendorId,
                vendorName,
                total,
                missing,
                itemNames,
                List.copyOf(dailyRows)
        );
    }
}
