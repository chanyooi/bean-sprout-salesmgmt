package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.DeliveryRecord;
import com.example.salesmgmt.domain.OrderSnapshot;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.entity.VendorPriceEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import com.example.salesmgmt.repository.SalesOrderRepository;
import com.example.salesmgmt.repository.VendorPriceRepository;
import com.example.salesmgmt.repository.VendorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        SalesPersistenceService.class,
        VendorRuleService.class,
        MonthlyCloseService.class,
        SalesPromotionService.class
})
class SalesPersistenceServiceTest {

    @Autowired
    SalesPersistenceService service;

    @Autowired
    VendorRepository vendorRepository;

    @Autowired
    SalesOrderRepository salesOrderRepository;

    @Autowired
    SalesItemRepository salesItemRepository;

    @Autowired
    VendorPriceRepository vendorPriceRepository;

    @Test
    void 저장할_때_거래처_단가를_판매품목에_복사한다() {
        registerPrice("옥계빅", "일반콩나물", "11000");

        service.save(List.of(record(
                "20260701-001",
                "옥계빅",
                "일반콩나물",
                "10",
                null
        )));

        var item = salesItemRepository.findAll().getFirst();
        assertThat(item.getUnitPrice()).isEqualByComparingTo("11000");
        assertThat(item.getLineAmount()).isEqualByComparingTo("110000");
    }

    @Test
    void 같은_주문번호와_품목의_수량이_같으면_건너뛴다() {
        DeliveryRecord record = record(
                "20260701-002",
                "명희네해장",
                "일반콩나물",
                "10",
                null
        );

        var first = service.save(List.of(record));
        var second = service.save(List.of(record));

        assertThat(first.savedItems()).isEqualTo(1);
        assertThat(first.updatedItems()).isZero();
        assertThat(first.deletedItems()).isZero();
        assertThat(second.savedItems()).isZero();
        assertThat(second.updatedItems()).isZero();
        assertThat(second.deletedItems()).isZero();
        assertThat(second.skippedDuplicateItems()).isEqualTo(1);
        assertThat(salesOrderRepository.count()).isEqualTo(1);
        assertThat(salesItemRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_주문번호와_품목의_수량이_달라지면_기존_자료와_금액을_수정한다() {
        registerPrice("옥계빅", "일반콩나물", "11000");

        service.save(List.of(record(
                "20260701-003",
                "옥계빅",
                "일반콩나물",
                "10",
                null
        )));

        var result = service.save(List.of(record(
                "20260701-003",
                "옥계빅",
                "일반콩나물",
                "12",
                null
        )));

        var item = salesItemRepository.findAll().getFirst();
        assertThat(result.savedItems()).isZero();
        assertThat(result.updatedItems()).isEqualTo(1);
        assertThat(result.deletedItems()).isZero();
        assertThat(result.skippedDuplicateItems()).isZero();
        assertThat(item.getQuantity()).isEqualByComparingTo("12");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("11000");
        assertThat(item.getLineAmount()).isEqualByComparingTo("132000");
        assertThat(salesItemRepository.count()).isEqualTo(1);
    }

    @Test
    void 재업로드한_주문에서_빠진_품목은_DB에서_삭제한다() {
        service.save(List.of(
                record(
                        "20260731-007",
                        "HS식자재도매유통",
                        "3.5kg일반",
                        "30",
                        null
                ),
                record(
                        "20260731-007",
                        "HS식자재도매유통",
                        "3.5kg곱슬",
                        "50",
                        null
                ),
                record(
                        "20260731-007",
                        "HS식자재도매유통",
                        "숙주",
                        "50",
                        null
                )
        ));

        var result = service.save(List.of(
                record(
                        "20260731-007",
                        "HS식자재도매유통",
                        "3.5kg일반",
                        "50",
                        null
                )
        ));

        assertThat(result.updatedItems()).isEqualTo(1);
        assertThat(result.deletedItems()).isEqualTo(2);
        assertThat(salesItemRepository.count()).isEqualTo(1);

        var remaining = salesItemRepository.findAll().getFirst();
        assertThat(remaining.getItemName()).isEqualTo("3.5kg일반");
        assertThat(remaining.getQuantity()).isEqualByComparingTo("50");
    }

    @Test
    void 일반품목은_단가표가_나중에_바뀌어도_재업로드할_때_판매당시_단가를_보존한다() {
        VendorEntity vendor = registerPrice(
                "옥계빅",
                "일반콩나물",
                "11000"
        );

        service.save(List.of(record(
                "20260701-004",
                "옥계빅",
                "일반콩나물",
                "10",
                null
        )));

        VendorPriceEntity price = vendorPriceRepository
                .findByVendor_IdAndItemName(
                        vendor.getId(),
                        "일반콩나물"
                )
                .orElseThrow();
        price.update(new BigDecimal("12000"), "옥계빅");

        service.save(List.of(record(
                "20260701-004",
                "옥계빅",
                "일반콩나물",
                "12",
                null
        )));

        var item = salesItemRepository.findAll().getFirst();
        assertThat(item.getUnitPrice()).isEqualByComparingTo("11000");
        assertThat(item.getLineAmount()).isEqualByComparingTo("132000");
    }

    @Test
    void 회수통은_재업로드된_주문별_단가와_수량을_함께_수정한다() {
        service.save(List.of(record(
                "20260701-005",
                "옥계빅",
                "회수통",
                "2",
                "3000"
        )));

        var result = service.save(List.of(record(
                "20260701-005",
                "옥계빅",
                "회수통",
                "3",
                "3500"
        )));

        var item = salesItemRepository.findAll().getFirst();
        assertThat(result.updatedItems()).isEqualTo(1);
        assertThat(result.deletedItems()).isZero();
        assertThat(item.getQuantity()).isEqualByComparingTo("3");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("-3500");
        assertThat(item.getLineAmount()).isEqualByComparingTo("-10500");
    }

    @Test
    void 회수통_주문별단가가_비어있어도_거래처기본단가가_있으면_음수로_계산한다() {
        registerPrice("옥계빅", "회수통", "3000");

        service.save(List.of(record(
                "20260701-006",
                "옥계빅",
                "회수통",
                "2",
                null
        )));

        var item = salesItemRepository.findAll().getFirst();
        assertThat(item.getUnitPrice()).isEqualByComparingTo("-3000");
        assertThat(item.getLineAmount()).isEqualByComparingTo("-6000");
    }

    @Test
    void 회수통_단가설정이_없으면_보증금없는_거래처로_보고_0원처리한다() {
        service.save(List.of(record(
                "20260701-007",
                "보증금없는거래처",
                "회수통",
                "4",
                null
        )));

        var item = salesItemRepository.findAll().getFirst();
        assertThat(item.getUnitPrice()).isEqualByComparingTo("0");
        assertThat(item.getLineAmount()).isEqualByComparingTo("0");
    }

    @Test
    void 재업로드에서_주문행이_전부_빈칸이면_기존_테스트주문을_삭제한다() {
        service.save(List.of(record(
                "20260705-007",
                "HS식자재도매유통",
                "3.5kg일반",
                "50",
                null
        )));

        Long orderId = salesOrderRepository
                .findByOrderNumber("20260705-007")
                .orElseThrow()
                .getId();

        OrderSnapshot blankRow = new OrderSnapshot(
                "20260705-007",
                LocalDate.of(2026, 7, 1),
                "HS식자재도매유통",
                "HS식자재도매유통",
                null,
                "",
                "",
                "20260705",
                11
        );

        var result = service.save(List.of(), List.of(blankRow));

        assertThat(result.deletedItems()).isEqualTo(1);
        assertThat(result.deletedOrders()).isEqualTo(1);
        assertThat(salesItemRepository.countBySalesOrder_Id(orderId)).isZero();
        assertThat(salesOrderRepository.findByOrderNumber("20260705-007"))
                .isEmpty();
    }

    private VendorEntity registerPrice(
            String vendorName,
            String itemName,
            String unitPrice
    ) {
        VendorEntity vendor = vendorRepository.save(new VendorEntity(
                vendorName,
                vendorName,
                true
        ));

        vendorPriceRepository.save(new VendorPriceEntity(
                vendor,
                itemName,
                new BigDecimal(unitPrice),
                vendorName
        ));

        return vendor;
    }

    private DeliveryRecord record(
            String orderNumber,
            String vendor,
            String item,
            String quantity,
            String returnContainerUnitPrice
    ) {
        return new DeliveryRecord(
                orderNumber,
                LocalDate.of(2026, 7, 1),
                vendor,
                "명희네해장".equals(vendor) ? "명희네" : vendor,
                item,
                new BigDecimal(quantity),
                returnContainerUnitPrice == null
                        ? null
                        : new BigDecimal(returnContainerUnitPrice),
                "카톡",
                "테스트",
                "20260701",
                5
        );
    }
}
