package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.MonthlySalesReport;
import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class MonthlySalesReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final SalesItemRepository salesItemRepository;

    public MonthlySalesReportService(SalesItemRepository salesItemRepository) {
        this.salesItemRepository = salesItemRepository;
    }

    @Transactional(readOnly = true)
    public Optional<YearMonth> findLatestSalesMonth() {
        return Optional.ofNullable(salesItemRepository.findLatestSalesDate())
                .map(YearMonth::from);
    }

    @Transactional(readOnly = true)
    public MonthlySalesReport createReport(YearMonth month) {
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();

        List<SalesItemEntity> items = salesItemRepository.findForMonthlyReport(
                startDate,
                endDate
        );

        BigDecimal confirmedSales = ZERO;
        BigDecimal totalQuantity = ZERO;
        long missingPriceCount = 0;

        Set<String> orderNumbers = new LinkedHashSet<>();
        Set<String> vendorNames = new LinkedHashSet<>();

        Map<String, VendorAccumulator> vendorAccumulators = new LinkedHashMap<>();
        Map<String, ItemAccumulator> itemAccumulators = new LinkedHashMap<>();
        Map<LocalDate, DailyAccumulator> dailyAccumulators = new LinkedHashMap<>();

        for (SalesItemEntity item : items) {
            String orderNumber = item.getSalesOrder().getOrderNumber();
            LocalDate deliveryDate = item.getSalesOrder().getDeliveryDate();
            String vendorName = item.getSalesOrder().getVendor().getInputName();
            String itemName = item.getItemName();
            BigDecimal quantity = item.getQuantity();
            BigDecimal lineAmount = item.getLineAmount();
            boolean missingPrice = item.getUnitPrice() == null || lineAmount == null;

            orderNumbers.add(orderNumber);
            vendorNames.add(vendorName);
            totalQuantity = totalQuantity.add(quantity);

            if (missingPrice) {
                missingPriceCount++;
            } else {
                confirmedSales = confirmedSales.add(lineAmount);
            }

            vendorAccumulators
                    .computeIfAbsent(vendorName, ignored -> new VendorAccumulator(vendorName))
                    .add(orderNumber, quantity, lineAmount, missingPrice);

            itemAccumulators
                    .computeIfAbsent(itemName, ignored -> new ItemAccumulator(itemName))
                    .add(vendorName, quantity, lineAmount, missingPrice);

            dailyAccumulators
                    .computeIfAbsent(deliveryDate, DailyAccumulator::new)
                    .add(orderNumber, vendorName, quantity, lineAmount, missingPrice);
        }

        List<MonthlySalesReport.VendorRow> vendorRows = vendorAccumulators.values()
                .stream()
                .map(VendorAccumulator::toRow)
                .sorted(Comparator
                        .comparing(MonthlySalesReport.VendorRow::confirmedSales)
                        .reversed()
                        .thenComparing(MonthlySalesReport.VendorRow::vendorName))
                .toList();

        List<MonthlySalesReport.ItemRow> itemRows = itemAccumulators.values()
                .stream()
                .map(ItemAccumulator::toRow)
                .sorted(Comparator
                        .comparing(MonthlySalesReport.ItemRow::confirmedSales)
                        .reversed()
                        .thenComparing(MonthlySalesReport.ItemRow::itemName))
                .toList();

        List<MonthlySalesReport.DailyRow> dailyRows = dailyAccumulators.values()
                .stream()
                .map(DailyAccumulator::toRow)
                .sorted(Comparator.comparing(MonthlySalesReport.DailyRow::deliveryDate))
                .toList();

        return new MonthlySalesReport(
                month,
                startDate,
                endDate,
                normalized(confirmedSales),
                normalized(totalQuantity),
                orderNumbers.size(),
                vendorNames.size(),
                items.size(),
                missingPriceCount,
                List.copyOf(vendorRows),
                List.copyOf(itemRows),
                List.copyOf(dailyRows)
        );
    }

    private static BigDecimal addAmount(BigDecimal current, BigDecimal lineAmount) {
        return lineAmount == null ? current : current.add(lineAmount);
    }

    private static BigDecimal normalized(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return ZERO;
        }
        return value.stripTrailingZeros();
    }

    private static final class VendorAccumulator {
        private final String vendorName;
        private BigDecimal confirmedSales = ZERO;
        private BigDecimal totalQuantity = ZERO;
        private final Set<String> orderNumbers = new LinkedHashSet<>();
        private long itemRecordCount;
        private long missingPriceCount;

        private VendorAccumulator(String vendorName) {
            this.vendorName = vendorName;
        }

        private void add(
                String orderNumber,
                BigDecimal quantity,
                BigDecimal lineAmount,
                boolean missingPrice
        ) {
            orderNumbers.add(orderNumber);
            totalQuantity = totalQuantity.add(quantity);
            confirmedSales = addAmount(confirmedSales, lineAmount);
            itemRecordCount++;
            if (missingPrice) {
                missingPriceCount++;
            }
        }

        private MonthlySalesReport.VendorRow toRow() {
            return new MonthlySalesReport.VendorRow(
                    vendorName,
                    normalized(confirmedSales),
                    normalized(totalQuantity),
                    orderNumbers.size(),
                    itemRecordCount,
                    missingPriceCount
            );
        }
    }

    private static final class ItemAccumulator {
        private final String itemName;
        private BigDecimal totalQuantity = ZERO;
        private BigDecimal confirmedSales = ZERO;
        private final Set<String> vendorNames = new LinkedHashSet<>();
        private long itemRecordCount;
        private long missingPriceCount;

        private ItemAccumulator(String itemName) {
            this.itemName = itemName;
        }

        private void add(
                String vendorName,
                BigDecimal quantity,
                BigDecimal lineAmount,
                boolean missingPrice
        ) {
            vendorNames.add(vendorName);
            totalQuantity = totalQuantity.add(quantity);
            confirmedSales = addAmount(confirmedSales, lineAmount);
            itemRecordCount++;
            if (missingPrice) {
                missingPriceCount++;
            }
        }

        private MonthlySalesReport.ItemRow toRow() {
            return new MonthlySalesReport.ItemRow(
                    itemName,
                    normalized(totalQuantity),
                    normalized(confirmedSales),
                    vendorNames.size(),
                    itemRecordCount,
                    missingPriceCount
            );
        }
    }

    private static final class DailyAccumulator {
        private final LocalDate deliveryDate;
        private BigDecimal confirmedSales = ZERO;
        private BigDecimal totalQuantity = ZERO;
        private final Set<String> orderNumbers = new LinkedHashSet<>();
        private final Set<String> vendorNames = new LinkedHashSet<>();
        private long itemRecordCount;
        private long missingPriceCount;

        private DailyAccumulator(LocalDate deliveryDate) {
            this.deliveryDate = deliveryDate;
        }

        private void add(
                String orderNumber,
                String vendorName,
                BigDecimal quantity,
                BigDecimal lineAmount,
                boolean missingPrice
        ) {
            orderNumbers.add(orderNumber);
            vendorNames.add(vendorName);
            totalQuantity = totalQuantity.add(quantity);
            confirmedSales = addAmount(confirmedSales, lineAmount);
            itemRecordCount++;
            if (missingPrice) {
                missingPriceCount++;
            }
        }

        private MonthlySalesReport.DailyRow toRow() {
            return new MonthlySalesReport.DailyRow(
                    deliveryDate,
                    normalized(confirmedSales),
                    normalized(totalQuantity),
                    orderNumbers.size(),
                    vendorNames.size(),
                    itemRecordCount,
                    missingPriceCount
            );
        }
    }
}
