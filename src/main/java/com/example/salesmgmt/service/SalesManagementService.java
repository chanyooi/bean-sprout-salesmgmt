package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.EditableSaleRow;
import com.example.salesmgmt.domain.VendorOption;
import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.SalesOrderEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import com.example.salesmgmt.repository.SalesOrderRepository;
import com.example.salesmgmt.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class SalesManagementService {

    private final SalesItemRepository salesItemRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final VendorRepository vendorRepository;
    private final MonthlyCloseService monthlyCloseService;

    public SalesManagementService(
            SalesItemRepository salesItemRepository,
            SalesOrderRepository salesOrderRepository,
            VendorRepository vendorRepository,
            MonthlyCloseService monthlyCloseService
    ) {
        this.salesItemRepository = salesItemRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.vendorRepository = vendorRepository;
        this.monthlyCloseService = monthlyCloseService;
    }

    @Transactional(readOnly = true)
    public YearMonth resolveMonth(String requestedMonth) {
        if (requestedMonth != null && !requestedMonth.isBlank()) {
            try {
                return YearMonth.parse(requestedMonth);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(
                        "조회 월 형식이 올바르지 않습니다."
                );
            }
        }

        LocalDate latestDate = salesItemRepository.findLatestSalesDate();
        return latestDate == null
                ? YearMonth.now()
                : YearMonth.from(latestDate);
    }

    @Transactional(readOnly = true)
    public List<EditableSaleRow> findRows(
            YearMonth month,
            Long vendorId
    ) {
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();

        return salesItemRepository
                .findForMonthlyReport(startDate, endDate)
                .stream()
                .filter(item -> vendorId == null
                        || item.getSalesOrder()
                        .getVendor()
                        .getId()
                        .equals(vendorId))
                .map(this::toRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VendorOption> findVendorOptions() {
        return vendorRepository.findAllByOrderByInputNameAsc()
                .stream()
                .map(vendor -> new VendorOption(
                        vendor.getId(),
                        vendor.getInputName(),
                        vendor.getStatementName()
                ))
                .toList();
    }

    @Transactional
    public void updateItem(
            Long itemId,
            BigDecimal quantity,
            BigDecimal unitPrice
    ) {
        SalesItemEntity item = salesItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "수정할 판매 품목을 찾을 수 없습니다."
                ));

        monthlyCloseService.assertOpen(
                item.getSalesOrder().getDeliveryDate()
        );

        BigDecimal salesUnitPrice = unitPrice;

        if ("회수통".equals(item.getItemName())) {
            salesUnitPrice =
                    ReturnContainerPricePolicy.toSalesUnitPrice(
                            unitPrice
                    );
        }

        item.updateManually(quantity, salesUnitPrice);
    }

    /**
     * 품목 하나를 삭제합니다.
     *
     * @return 해당 품목이 주문의 마지막 품목이어서 주문도 같이 삭제되었으면 true
     */
    @Transactional
    public boolean deleteItem(Long itemId) {
        SalesItemEntity item = salesItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "삭제할 판매 품목을 찾을 수 없습니다."
                ));

        monthlyCloseService.assertOpen(
                item.getSalesOrder().getDeliveryDate()
        );

        Long orderId = item.getSalesOrder().getId();

        salesItemRepository.delete(item);
        salesItemRepository.flush();

        if (salesItemRepository.countBySalesOrder_Id(orderId) == 0) {
            salesOrderRepository.deleteById(orderId);
            return true;
        }

        return false;
    }

    /**
     * 주문과 그 주문에 포함된 모든 품목을 삭제합니다.
     *
     * @return 삭제된 품목 수
     */
    @Transactional
    public long deleteOrder(Long orderId) {
        SalesOrderEntity order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "삭제할 주문을 찾을 수 없습니다."
                ));

        monthlyCloseService.assertOpen(
                order.getDeliveryDate()
        );

        long deletedItemCount =
                salesItemRepository.countBySalesOrder_Id(orderId);

        salesItemRepository.deleteAllBySalesOrder_Id(orderId);
        salesItemRepository.flush();
        salesOrderRepository.delete(order);

        return deletedItemCount;
    }

    private EditableSaleRow toRow(SalesItemEntity item) {
        SalesOrderEntity order = item.getSalesOrder();

        return new EditableSaleRow(
                item.getId(),
                order.getId(),
                order.getOrderNumber(),
                order.getDeliveryDate(),
                order.getVendor().getId(),
                order.getVendor().getInputName(),
                order.getVendor().getStatementName(),
                item.getItemName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineAmount(),
                order.getDeliveryMethod(),
                order.getNote()
        );
    }
}
