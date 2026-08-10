package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.SalesOrderEntity;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import com.example.salesmgmt.repository.SalesOrderRepository;
import com.example.salesmgmt.repository.VendorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(SalesManagementService.class)
class SalesManagementServiceTest {

    @Autowired
    SalesManagementService service;

    @Autowired
    VendorRepository vendorRepository;

    @Autowired
    SalesOrderRepository salesOrderRepository;

    @Autowired
    SalesItemRepository salesItemRepository;

    @Test
    void 수량과_단가를_수정하면_금액을_다시_계산한다() {
        SalesItemEntity item = saveItem(
                "20260701-001",
                "일반콩나물",
                "10",
                "11000"
        );

        service.updateItem(
                item.getId(),
                new BigDecimal("12"),
                new BigDecimal("12000")
        );

        SalesItemEntity updated =
                salesItemRepository.findById(item.getId()).orElseThrow();

        assertThat(updated.getQuantity()).isEqualByComparingTo("12");
        assertThat(updated.getUnitPrice()).isEqualByComparingTo("12000");
        assertThat(updated.getLineAmount()).isEqualByComparingTo("144000");
    }

    @Test
    void 회수통_단가를_양수로_입력해도_매출에는_음수로_저장한다() {
        SalesItemEntity item = saveItem(
                "20260701-010",
                "회수통",
                "2",
                "-3000"
        );

        service.updateItem(
                item.getId(),
                new BigDecimal("3"),
                new BigDecimal("3500")
        );

        SalesItemEntity updated =
                salesItemRepository.findById(item.getId()).orElseThrow();

        assertThat(updated.getQuantity()).isEqualByComparingTo("3");
        assertThat(updated.getUnitPrice()).isEqualByComparingTo("-3500");
        assertThat(updated.getLineAmount()).isEqualByComparingTo("-10500");
    }

    @Test
    void 품목을_삭제해도_다른_품목이_있으면_주문은_남는다() {
        SalesOrderEntity order = saveOrder("20260701-002");
        SalesItemEntity first = salesItemRepository.save(
                new SalesItemEntity(
                        order,
                        "일반콩나물",
                        new BigDecimal("10"),
                        new BigDecimal("11000")
                )
        );
        salesItemRepository.save(
                new SalesItemEntity(
                        order,
                        "숙주",
                        new BigDecimal("5"),
                        new BigDecimal("3800")
                )
        );

        boolean orderDeleted = service.deleteItem(first.getId());

        assertThat(orderDeleted).isFalse();
        assertThat(salesOrderRepository.existsById(order.getId())).isTrue();
        assertThat(
                salesItemRepository.countBySalesOrder_Id(order.getId())
        ).isEqualTo(1);
    }

    @Test
    void 마지막_품목을_삭제하면_빈_주문도_함께_삭제한다() {
        SalesItemEntity item = saveItem(
                "20260701-003",
                "일반콩나물",
                "10",
                "11000"
        );
        Long orderId = item.getSalesOrder().getId();

        boolean orderDeleted = service.deleteItem(item.getId());

        assertThat(orderDeleted).isTrue();
        assertThat(salesItemRepository.existsById(item.getId())).isFalse();
        assertThat(salesOrderRepository.existsById(orderId)).isFalse();
    }

    @Test
    void 주문_전체_삭제는_포함된_모든_품목을_삭제한다() {
        SalesOrderEntity order = saveOrder("20260701-004");

        salesItemRepository.save(
                new SalesItemEntity(
                        order,
                        "일반콩나물",
                        new BigDecimal("10"),
                        new BigDecimal("11000")
                )
        );
        salesItemRepository.save(
                new SalesItemEntity(
                        order,
                        "숙주",
                        new BigDecimal("5"),
                        new BigDecimal("3800")
                )
        );

        long deletedCount = service.deleteOrder(order.getId());

        assertThat(deletedCount).isEqualTo(2);
        assertThat(
                salesItemRepository.countBySalesOrder_Id(order.getId())
        ).isZero();
        assertThat(salesOrderRepository.existsById(order.getId())).isFalse();
    }

    private SalesItemEntity saveItem(
            String orderNumber,
            String itemName,
            String quantity,
            String unitPrice
    ) {
        SalesOrderEntity order = saveOrder(orderNumber);

        return salesItemRepository.save(new SalesItemEntity(
                order,
                itemName,
                new BigDecimal(quantity),
                new BigDecimal(unitPrice)
        ));
    }

    private SalesOrderEntity saveOrder(String orderNumber) {
        VendorEntity vendor = vendorRepository.findByInputName("옥계빅")
                .orElseGet(() -> vendorRepository.save(
                        new VendorEntity(
                                "옥계빅",
                                "옥계빅",
                                true
                        )
                ));

        return salesOrderRepository.save(new SalesOrderEntity(
                orderNumber,
                LocalDate.of(2026, 7, 1),
                vendor,
                null,
                "",
                "",
                "20260701",
                5
        ));
    }
}
