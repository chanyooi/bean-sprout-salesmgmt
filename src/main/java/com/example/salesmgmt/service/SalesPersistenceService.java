package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.DeliveryRecord;
import com.example.salesmgmt.domain.OrderSnapshot;
import com.example.salesmgmt.domain.SaveResult;
import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.SalesOrderEntity;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.exception.SalesDataConflictException;
import com.example.salesmgmt.repository.SalesItemRepository;
import com.example.salesmgmt.repository.SalesOrderRepository;
import com.example.salesmgmt.repository.VendorPriceRepository;
import com.example.salesmgmt.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SalesPersistenceService {

    private final VendorRepository vendorRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesItemRepository salesItemRepository;
    private final VendorPriceRepository vendorPriceRepository;
    private final VendorRuleService vendorRuleService;
    private final MonthlyCloseService monthlyCloseService;
    private final SalesPromotionService salesPromotionService;

    public SalesPersistenceService(
            VendorRepository vendorRepository,
            SalesOrderRepository salesOrderRepository,
            SalesItemRepository salesItemRepository,
            VendorPriceRepository vendorPriceRepository,
            VendorRuleService vendorRuleService,
            MonthlyCloseService monthlyCloseService,
            SalesPromotionService salesPromotionService
    ) {
        this.vendorRepository = vendorRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesItemRepository = salesItemRepository;
        this.vendorPriceRepository = vendorPriceRepository;
        this.vendorRuleService = vendorRuleService;
        this.monthlyCloseService = monthlyCloseService;
        this.salesPromotionService = salesPromotionService;
    }

    /**
     * 기존 테스트/다른 내부 호출 호환용입니다.
     * 품목이 있는 주문만 전달되는 경우에는 그 주문들만 동기화합니다.
     */
    @Transactional
    public SaveResult save(List<DeliveryRecord> records) {
        return save(records, snapshotsFromRecords(records));
    }

    /**
     * input_data.xlsx의 거래처 행 전체 상태를 DB와 동기화합니다.
     *
     * - 품목이 있으면: 신규/수정/삭제를 동기화
     * - 품목이 하나도 없으면: 기존에 같은 주문번호가 DB에 있을 때
     *   그 주문과 모든 품목을 삭제
     *
     * 그래서 과거 테스트로 50박스를 넣었다가 엑셀에서 그 행을 전부
     * 빈칸으로 돌려도, 재업로드하면 DB의 테스트 주문이 제거됩니다.
     */
    @Transactional
    public SaveResult save(
            List<DeliveryRecord> records,
            List<OrderSnapshot> orderSnapshots
    ) {
        for (OrderSnapshot snapshot : orderSnapshots) {
            monthlyCloseService.assertOpen(snapshot.deliveryDate());
        }

        for (DeliveryRecord record : records) {
            monthlyCloseService.assertOpen(record.deliveryDate());
        }

        Map<String, VendorEntity> vendorCache = new HashMap<>();
        Map<String, BigDecimal> priceCache = new HashMap<>();

        int createdVendors = 0;
        int createdOrders = 0;
        int savedItems = 0;
        int updatedItems = 0;
        int deletedItems = 0;
        int deletedOrders = 0;
        int skippedDuplicateItems = 0;

        Map<String, List<DeliveryRecord>> recordsByOrder = records.stream()
                .collect(Collectors.groupingBy(
                        DeliveryRecord::orderNumber,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<String, OrderSnapshot> snapshotsByOrder = new LinkedHashMap<>();
        for (OrderSnapshot snapshot : orderSnapshots) {
            OrderSnapshot previous = snapshotsByOrder.putIfAbsent(
                    snapshot.orderNumber(),
                    snapshot
            );

            if (previous != null) {
                validateSnapshotMetadata(previous, snapshot);
            }
        }

        // orderSnapshots가 없는 과거 내부 호출까지 안전하게 지원합니다.
        if (snapshotsByOrder.isEmpty() && !records.isEmpty()) {
            for (OrderSnapshot snapshot : snapshotsFromRecords(records)) {
                snapshotsByOrder.put(snapshot.orderNumber(), snapshot);
            }
        }

        for (Map.Entry<String, OrderSnapshot> snapshotEntry
                : snapshotsByOrder.entrySet()) {

            String orderNumber = snapshotEntry.getKey();
            OrderSnapshot snapshot = snapshotEntry.getValue();
            List<DeliveryRecord> rawOrderRecords = recordsByOrder.remove(
                    orderNumber
            );

            // 이 행의 모든 품목이 빈칸/0이면 기존 테스트 주문을 완전히 제거합니다.
            if (rawOrderRecords == null || rawOrderRecords.isEmpty()) {
                var existingOrder = salesOrderRepository.findByOrderNumber(
                        orderNumber
                );

                if (existingOrder.isEmpty()) {
                    continue;
                }

                SalesOrderEntity salesOrder = existingOrder.get();
                validateExistingOrder(snapshot, salesOrder);

                long existingItemCount =
                        salesItemRepository.countBySalesOrder_Id(
                                salesOrder.getId()
                        );

                salesItemRepository.deleteAllBySalesOrder_Id(
                        salesOrder.getId()
                );
                salesItemRepository.flush();
                salesOrderRepository.delete(salesOrder);

                deletedItems += Math.toIntExact(existingItemCount);
                deletedOrders++;
                continue;
            }

            OrderProcessResult result = processOrder(
                    rawOrderRecords,
                    vendorCache,
                    priceCache
            );

            createdVendors += result.createdVendors();
            createdOrders += result.createdOrders();
            savedItems += result.savedItems();
            updatedItems += result.updatedItems();
            deletedItems += result.deletedItems();
            skippedDuplicateItems += result.skippedDuplicateItems();
        }

        // 혹시 snapshots에 없는 판매기록이 들어온 경우도 유실하지 않습니다.
        for (List<DeliveryRecord> remainingRecords
                : recordsByOrder.values()) {
            OrderProcessResult result = processOrder(
                    remainingRecords,
                    vendorCache,
                    priceCache
            );

            createdVendors += result.createdVendors();
            createdOrders += result.createdOrders();
            savedItems += result.savedItems();
            updatedItems += result.updatedItems();
            deletedItems += result.deletedItems();
            skippedDuplicateItems += result.skippedDuplicateItems();
        }

        return new SaveResult(
                createdVendors,
                createdOrders,
                savedItems,
                updatedItems,
                deletedItems,
                deletedOrders,
                skippedDuplicateItems
        );
    }

    private OrderProcessResult processOrder(
            List<DeliveryRecord> rawOrderRecords,
            Map<String, VendorEntity> vendorCache,
            Map<String, BigDecimal> priceCache
    ) {
        if (rawOrderRecords == null || rawOrderRecords.isEmpty()) {
            return OrderProcessResult.empty();
        }

        int createdVendors = 0;
        int createdOrders = 0;
        int savedItems = 0;
        int updatedItems = 0;
        int deletedItems = 0;
        int skippedDuplicateItems = 0;

        Map<String, DeliveryRecord> incomingByItem = new LinkedHashMap<>();
        for (DeliveryRecord record : rawOrderRecords) {
            if (incomingByItem.putIfAbsent(record.item(), record) != null) {
                skippedDuplicateItems++;
            }
        }

        List<DeliveryRecord> orderRecords =
                new ArrayList<>(incomingByItem.values());
        DeliveryRecord representative = orderRecords.getFirst();

        validateOrderGroupMetadata(representative, orderRecords);

        VendorResolution vendorResolution = resolveVendor(
                representative,
                vendorCache
        );
        VendorEntity vendor = vendorResolution.vendor();
        if (vendorResolution.created()) {
            createdVendors++;
        }

        OrderResolution orderResolution = resolveOrder(
                representative,
                vendor
        );
        SalesOrderEntity salesOrder = orderResolution.salesOrder();
        if (orderResolution.created()) {
            createdOrders++;
        }

        List<SalesItemEntity> existingItems =
                salesItemRepository.findAllBySalesOrder_Id(
                        salesOrder.getId()
                );

        Map<String, SalesItemEntity> existingByItem =
                existingItems.stream().collect(Collectors.toMap(
                        SalesItemEntity::getItemName,
                        item -> item
                ));

        Set<String> incomingItemNames = incomingByItem.keySet();

        List<SalesItemEntity> itemsToDelete = existingItems.stream()
                .filter(item -> !incomingItemNames.contains(
                        item.getItemName()
                ))
                .toList();

        if (!itemsToDelete.isEmpty()) {
            salesItemRepository.deleteAll(itemsToDelete);
            deletedItems += itemsToDelete.size();
        }

        for (DeliveryRecord record : orderRecords) {
            BigDecimal resolvedUnitPrice = resolveUnitPrice(
                    record,
                    vendor,
                    priceCache
            );

            SalesItemEntity existingItem =
                    existingByItem.get(record.item());

            if (existingItem != null) {
                boolean hasPromotionPrice =
                        salesPromotionService.findEventPrice(
                                vendor.getId(),
                                record.item(),
                                record.deliveryDate()
                        ) != null;

                boolean replaceUnitPrice =
                        ("회수통".equals(record.item())
                                && record.returnContainerUnitPrice() != null)
                                || hasPromotionPrice;

                boolean changed = existingItem.updateFromUpload(
                        record.quantity(),
                        resolvedUnitPrice,
                        replaceUnitPrice
                );

                if (changed) {
                    updatedItems++;
                } else {
                    skippedDuplicateItems++;
                }
                continue;
            }

            salesItemRepository.save(new SalesItemEntity(
                    salesOrder,
                    record.item(),
                    record.quantity(),
                    resolvedUnitPrice
            ));
            savedItems++;
        }

        return new OrderProcessResult(
                createdVendors,
                createdOrders,
                savedItems,
                updatedItems,
                deletedItems,
                skippedDuplicateItems
        );
    }

    private List<OrderSnapshot> snapshotsFromRecords(
            List<DeliveryRecord> records
    ) {
        Map<String, OrderSnapshot> snapshots = new LinkedHashMap<>();

        for (DeliveryRecord record : records) {
            snapshots.putIfAbsent(
                    record.orderNumber(),
                    new OrderSnapshot(
                            record.orderNumber(),
                            record.deliveryDate(),
                            record.inputVendor(),
                            record.statementVendor(),
                            record.returnContainerUnitPrice(),
                            record.deliveryMethod(),
                            record.note(),
                            record.sourceSheet(),
                            record.sourceRow()
                    )
            );
        }

        return List.copyOf(snapshots.values());
    }

    private VendorResolution resolveVendor(
            DeliveryRecord record,
            Map<String, VendorEntity> vendorCache
    ) {
        VendorEntity cached = vendorCache.get(record.inputVendor());
        if (cached != null) {
            return new VendorResolution(cached, false);
        }

        var existingVendor = vendorRepository.findByInputName(
                record.inputVendor()
        );

        VendorEntity vendor;
        boolean created;

        if (existingVendor.isPresent()) {
            vendor = existingVendor.get();
            vendor.updateStatementSettings(
                    record.statementVendor(),
                    vendorRuleService.hasStatementTemplate(
                            record.inputVendor()
                    )
            );
            created = false;
        } else {
            vendor = vendorRepository.save(new VendorEntity(
                    record.inputVendor(),
                    record.statementVendor(),
                    vendorRuleService.hasStatementTemplate(
                            record.inputVendor()
                    )
            ));
            created = true;
        }

        vendorCache.put(record.inputVendor(), vendor);
        return new VendorResolution(vendor, created);
    }

    private OrderResolution resolveOrder(
            DeliveryRecord record,
            VendorEntity vendor
    ) {
        var existingOrder = salesOrderRepository.findByOrderNumber(
                record.orderNumber()
        );

        if (existingOrder.isPresent()) {
            SalesOrderEntity salesOrder = existingOrder.get();
            validateExistingOrder(record, salesOrder);
            salesOrder.fillMissingMetadata(
                    record.returnContainerUnitPrice(),
                    record.deliveryMethod(),
                    record.note()
            );
            return new OrderResolution(salesOrder, false);
        }

        SalesOrderEntity salesOrder =
                salesOrderRepository.save(new SalesOrderEntity(
                        record.orderNumber(),
                        record.deliveryDate(),
                        vendor,
                        record.returnContainerUnitPrice(),
                        record.deliveryMethod(),
                        record.note(),
                        record.sourceSheet(),
                        record.sourceRow()
                ));

        return new OrderResolution(salesOrder, true);
    }

    private BigDecimal resolveUnitPrice(
            DeliveryRecord record,
            VendorEntity vendor,
            Map<String, BigDecimal> priceCache
    ) {
        String key = vendor.getId() + "\u0000" + record.item();

        if ("회수통".equals(record.item())) {
            BigDecimal configuredPrice =
                    record.returnContainerUnitPrice();

            if (configuredPrice == null) {
                if (priceCache.containsKey(key)) {
                    configuredPrice = priceCache.get(key);
                } else {
                    configuredPrice = vendorPriceRepository
                            .findByVendor_IdAndItemName(
                                    vendor.getId(),
                                    record.item()
                            )
                            .map(priceEntity -> priceEntity.getUnitPrice())
                            .orElse(null);
                    priceCache.put(key, configuredPrice);
                }
            }

            return ReturnContainerPricePolicy.toSalesUnitPrice(
                    configuredPrice
            );
        }

        BigDecimal promotionPrice =
                salesPromotionService.findEventPrice(
                        vendor.getId(),
                        record.item(),
                        record.deliveryDate()
                );

        if (promotionPrice != null) {
            return promotionPrice;
        }

        if (priceCache.containsKey(key)) {
            return priceCache.get(key);
        }

        BigDecimal price = vendorPriceRepository
                .findByVendor_IdAndItemName(
                        vendor.getId(),
                        record.item()
                )
                .map(priceEntity -> priceEntity.getUnitPrice())
                .orElse(null);

        priceCache.put(key, price);
        return price;
    }

    private void validateOrderGroupMetadata(
            DeliveryRecord representative,
            List<DeliveryRecord> orderRecords
    ) {
        for (DeliveryRecord record : orderRecords) {
            if (!representative.deliveryDate().equals(
                    record.deliveryDate()
            )) {
                throw new SalesDataConflictException(
                        "한 업로드 파일 안에서 주문번호 "
                                + representative.orderNumber()
                                + "의 날짜가 서로 다릅니다."
                );
            }

            if (!representative.inputVendor().equals(
                    record.inputVendor()
            )) {
                throw new SalesDataConflictException(
                        "한 업로드 파일 안에서 주문번호 "
                                + representative.orderNumber()
                                + "의 거래처가 서로 다릅니다."
                );
            }
        }
    }

    private void validateSnapshotMetadata(
            OrderSnapshot first,
            OrderSnapshot second
    ) {
        if (!first.deliveryDate().equals(second.deliveryDate())
                || !first.inputVendor().equals(second.inputVendor())) {
            throw new SalesDataConflictException(
                    "한 업로드 파일 안에서 주문번호 "
                            + first.orderNumber()
                            + "가 서로 다른 날짜 또는 거래처에 사용되었습니다."
            );
        }
    }

    private void validateExistingOrder(
            DeliveryRecord incoming,
            SalesOrderEntity existing
    ) {
        validateExistingOrder(
                incoming.orderNumber(),
                incoming.deliveryDate(),
                incoming.inputVendor(),
                existing
        );
    }

    private void validateExistingOrder(
            OrderSnapshot incoming,
            SalesOrderEntity existing
    ) {
        validateExistingOrder(
                incoming.orderNumber(),
                incoming.deliveryDate(),
                incoming.inputVendor(),
                existing
        );
    }

    private void validateExistingOrder(
            String orderNumber,
            java.time.LocalDate deliveryDate,
            String inputVendor,
            SalesOrderEntity existing
    ) {
        if (!existing.getDeliveryDate().equals(deliveryDate)) {
            throw new SalesDataConflictException(
                    "주문번호 " + orderNumber
                            + "가 이미 다른 날짜로 저장되어 있습니다. "
                            + "기존 날짜: " + existing.getDeliveryDate()
                            + ", 새 날짜: " + deliveryDate
            );
        }

        if (!existing.getVendor().getInputName().equals(inputVendor)) {
            throw new SalesDataConflictException(
                    "주문번호 " + orderNumber
                            + "가 이미 다른 거래처로 저장되어 있습니다. "
                            + "기존 거래처: "
                            + existing.getVendor().getInputName()
                            + ", 새 거래처: "
                            + inputVendor
            );
        }
    }

    private record VendorResolution(
            VendorEntity vendor,
            boolean created
    ) {
    }

    private record OrderResolution(
            SalesOrderEntity salesOrder,
            boolean created
    ) {
    }

    private record OrderProcessResult(
            int createdVendors,
            int createdOrders,
            int savedItems,
            int updatedItems,
            int deletedItems,
            int skippedDuplicateItems
    ) {
        private static OrderProcessResult empty() {
            return new OrderProcessResult(0, 0, 0, 0, 0, 0);
        }
    }
}
