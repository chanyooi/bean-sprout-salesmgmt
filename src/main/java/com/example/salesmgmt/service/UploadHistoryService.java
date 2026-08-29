package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.OrderSnapshot;
import com.example.salesmgmt.domain.SaveResult;
import com.example.salesmgmt.entity.*;
import com.example.salesmgmt.repository.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class UploadHistoryService {

    private static final int BACKUP_QUERY_CHUNK = 500;

    private final UploadHistoryRepository historyRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesItemRepository itemRepository;
    private final VendorRepository vendorRepository;
    private final ObjectMapper objectMapper;

    public UploadHistoryService(
            UploadHistoryRepository historyRepository,
            SalesOrderRepository orderRepository,
            SalesItemRepository itemRepository,
            VendorRepository vendorRepository,
            ObjectMapper objectMapper
    ) {
        this.historyRepository = historyRepository;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.vendorRepository = vendorRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 과거 내부 호출 호환용 전체 백업입니다.
     * 일반 Excel 업로드에서는 아래의 범위 백업 메서드를 사용합니다.
     */
    @Transactional(readOnly = true)
    public String captureSalesSnapshot() {
        List<SalesOrderEntity> allOrders = orderRepository.findAllForBackup();
        List<SalesItemEntity> allItems = itemRepository.findAllForBackup();
        return serializeBackup(new SalesBackup(
                false,
                List.of(),
                toOrderBackups(allOrders, allItems)
        ));
    }

    /**
     * 업로드가 실제로 건드릴 주문번호만 백업합니다.
     * 예전에는 업로드할 때마다 과거 전체 판매 DB를 전부 읽어 JSON으로 만들었기 때문에
     * 데이터가 쌓일수록 업로드 시간과 메모리 사용량이 계속 증가했습니다.
     */
    @Transactional(readOnly = true)
    public String captureSalesSnapshot(List<OrderSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return serializeBackup(new SalesBackup(true, List.of(), List.of()));
        }

        List<String> orderNumbers = new ArrayList<>(new LinkedHashSet<>(
                snapshots.stream()
                        .map(OrderSnapshot::orderNumber)
                        .filter(number -> number != null && !number.isBlank())
                        .toList()
        ));

        List<SalesOrderEntity> orders = new ArrayList<>();
        List<SalesItemEntity> items = new ArrayList<>();

        for (int start = 0; start < orderNumbers.size(); start += BACKUP_QUERY_CHUNK) {
            int end = Math.min(start + BACKUP_QUERY_CHUNK, orderNumbers.size());
            List<String> chunk = orderNumbers.subList(start, end);
            orders.addAll(orderRepository.findForBackupByOrderNumbers(chunk));
            items.addAll(itemRepository.findForBackupByOrderNumbers(chunk));
        }

        return serializeBackup(new SalesBackup(
                true,
                List.copyOf(orderNumbers),
                toOrderBackups(orders, items)
        ));
    }

    private List<OrderBackup> toOrderBackups(
            List<SalesOrderEntity> orders,
            List<SalesItemEntity> items
    ) {
        Map<Long, List<ItemBackup>> itemsByOrderId = new LinkedHashMap<>();
        for (SalesItemEntity item : items) {
            Long orderId = item.getSalesOrder().getId();
            itemsByOrderId
                    .computeIfAbsent(orderId, ignored -> new ArrayList<>())
                    .add(new ItemBackup(
                            item.getItemName(),
                            item.getQuantity(),
                            item.getUnitPrice()
                    ));
        }

        List<OrderBackup> backups = new ArrayList<>(orders.size());
        for (SalesOrderEntity order : orders) {
            backups.add(new OrderBackup(
                    order.getOrderNumber(),
                    order.getDeliveryDate(),
                    order.getVendor().getInputName(),
                    order.getReturnContainerUnitPrice(),
                    order.getDeliveryMethod(),
                    order.getNote(),
                    order.getSourceSheet(),
                    order.getSourceRow(),
                    List.copyOf(itemsByOrderId.getOrDefault(order.getId(), List.of()))
            ));
        }
        return List.copyOf(backups);
    }

    private String serializeBackup(SalesBackup backup) {
        try {
            return objectMapper.writeValueAsString(backup);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "업로드 전 백업을 만들지 못했습니다.",
                    exception
            );
        }
    }

    @Transactional
    public void recordSuccess(
            String fileName,
            String beforeSnapshotJson,
            SaveResult result
    ) {
        historyRepository.save(new UploadHistoryEntity(
                safeFileName(fileName),
                result.createdVendors(),
                result.createdOrders(),
                result.savedItems(),
                result.updatedItems(),
                result.deletedItems(),
                result.deletedOrders(),
                result.skippedDuplicateItems(),
                beforeSnapshotJson
        ));
    }

    @Transactional(readOnly = true)
    public List<UploadHistoryEntity> findRecent() {
        return historyRepository.findTop50ByOrderByUploadedAtDesc();
    }

    @Transactional(readOnly = true)
    public Long latestRestorableId() {
        return historyRepository
                .findFirstByRestoredFalseOrderByUploadedAtDesc()
                .map(UploadHistoryEntity::getId)
                .orElse(null);
    }

    @Transactional
    public void restoreLatest(Long historyId) {
        UploadHistoryEntity latest = historyRepository
                .findFirstByRestoredFalseOrderByUploadedAtDesc()
                .orElseThrow(() -> new IllegalArgumentException(
                        "복구할 업로드 이력이 없습니다."
                ));

        if (!latest.getId().equals(historyId)) {
            throw new IllegalArgumentException(
                    "안전을 위해 가장 최근 업로드만 복구할 수 있습니다."
            );
        }

        SalesBackup backup;
        try {
            backup = objectMapper.readValue(
                    latest.getBeforeSnapshotJson(),
                    SalesBackup.class
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "백업 데이터를 읽지 못했습니다.",
                    exception
            );
        }

        if (Boolean.TRUE.equals(backup.scoped())) {
            restoreScopedBackup(backup);
        } else {
            restoreLegacyFullBackup(backup);
        }

        latest.markRestored();
    }

    /** 새 방식: 이번 업로드가 건드린 주문만 원래 상태로 되돌립니다. */
    private void restoreScopedBackup(SalesBackup backup) {
        List<String> affectedOrderNumbers = backup.orderNumbers() == null
                ? List.of()
                : backup.orderNumbers();

        for (String orderNumber : affectedOrderNumbers) {
            orderRepository.findByOrderNumber(orderNumber).ifPresent(order -> {
                itemRepository.deleteAllBySalesOrder_Id(order.getId());
                orderRepository.delete(order);
            });
        }
        itemRepository.flush();
        orderRepository.flush();

        restoreOrders(backup.orders());
    }

    /** 기존 업로드 이력은 예전 방식 그대로 전체 DB 복구가 가능하도록 유지합니다. */
    private void restoreLegacyFullBackup(SalesBackup backup) {
        itemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        restoreOrders(backup.orders());
    }

    private void restoreOrders(List<OrderBackup> savedOrders) {
        if (savedOrders == null) {
            return;
        }

        for (OrderBackup savedOrder : savedOrders) {
            VendorEntity vendor = vendorRepository
                    .findByInputName(savedOrder.vendorName())
                    .orElseThrow(() -> new IllegalStateException(
                            "복구에 필요한 거래처가 없습니다: "
                                    + savedOrder.vendorName()
                    ));

            SalesOrderEntity order = orderRepository.save(
                    new SalesOrderEntity(
                            savedOrder.orderNumber(),
                            savedOrder.deliveryDate(),
                            vendor,
                            savedOrder.returnContainerUnitPrice(),
                            savedOrder.deliveryMethod(),
                            savedOrder.note(),
                            savedOrder.sourceSheet(),
                            savedOrder.sourceRow()
                    )
            );

            if (savedOrder.items() == null) {
                continue;
            }
            for (ItemBackup savedItem : savedOrder.items()) {
                itemRepository.save(new SalesItemEntity(
                        order,
                        savedItem.itemName(),
                        savedItem.quantity(),
                        savedItem.unitPrice()
                ));
            }
        }
    }

    private String safeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "input_data.xlsx";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    public record SalesBackup(
            Boolean scoped,
            List<String> orderNumbers,
            List<OrderBackup> orders
    ) {}

    public record OrderBackup(
            String orderNumber,
            LocalDate deliveryDate,
            String vendorName,
            BigDecimal returnContainerUnitPrice,
            String deliveryMethod,
            String note,
            String sourceSheet,
            Integer sourceRow,
            List<ItemBackup> items
    ) {}

    public record ItemBackup(
            String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice
    ) {}
}
