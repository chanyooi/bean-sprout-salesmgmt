package com.example.salesmgmt.service;

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
import java.util.List;

@Service
public class UploadHistoryService {

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

    @Transactional(readOnly = true)
    public String captureSalesSnapshot() {
        List<OrderBackup> orders = new ArrayList<>();

        for (SalesOrderEntity order : orderRepository.findAll()) {
            List<ItemBackup> items = itemRepository
                    .findAllBySalesOrder_Id(order.getId())
                    .stream()
                    .map(item -> new ItemBackup(
                            item.getItemName(),
                            item.getQuantity(),
                            item.getUnitPrice()
                    ))
                    .toList();

            orders.add(new OrderBackup(
                    order.getOrderNumber(),
                    order.getDeliveryDate(),
                    order.getVendor().getInputName(),
                    order.getReturnContainerUnitPrice(),
                    order.getDeliveryMethod(),
                    order.getNote(),
                    order.getSourceSheet(),
                    order.getSourceRow(),
                    items
            ));
        }

        try {
            return objectMapper.writeValueAsString(
                    new SalesBackup(orders)
            );
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

    /**
     * 가장 최근 업로드 1건만 되돌립니다.
     * 오래된 이력으로 임의 복구하면 그 이후 정상 업로드도 함께 사라질 수 있어
     * 안전을 위해 최신 미복구 이력만 허용합니다.
     */
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

        itemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();

        for (OrderBackup savedOrder : backup.orders()) {
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

            for (ItemBackup savedItem : savedOrder.items()) {
                itemRepository.save(new SalesItemEntity(
                        order,
                        savedItem.itemName(),
                        savedItem.quantity(),
                        savedItem.unitPrice()
                ));
            }
        }

        latest.markRestored();
    }

    private String safeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "input_data.xlsx";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    public record SalesBackup(List<OrderBackup> orders) {}
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
