package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.SalesOrderEntity;
import com.example.salesmgmt.entity.VendorEntity;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ReturnContainerDataCorrectionService.class)
class ReturnContainerDataCorrectionServiceTest {

    @Autowired
    ReturnContainerDataCorrectionService service;

    @Autowired
    VendorRepository vendorRepository;

    @Autowired
    SalesOrderRepository salesOrderRepository;

    @Autowired
    SalesItemRepository salesItemRepository;

    @Autowired
    VendorPriceRepository vendorPriceRepository;

    @Test
    void 기존에_양수로_저장된_회수통은_같은금액의_음수로_보정한다() {
        SalesOrderEntity order = saveOrder(
                "20260701-020",
                "3000"
        );

        SalesItemEntity item = salesItemRepository.save(
                new SalesItemEntity(
                        order,
                        "회수통",
                        new BigDecimal("2"),
                        new BigDecimal("3000")
                )
        );

        int corrected = service.correctExistingSales();

        SalesItemEntity result = salesItemRepository
                .findById(item.getId())
                .orElseThrow();

        assertThat(corrected).isEqualTo(1);
        assertThat(result.getUnitPrice()).isEqualByComparingTo("-3000");
        assertThat(result.getLineAmount()).isEqualByComparingTo("-6000");
    }

    @Test
    void 단가가_없고_회수통설정도_없으면_0원으로_보정한다() {
        SalesOrderEntity order = saveOrder(
                "20260701-021",
                null
        );

        SalesItemEntity item = salesItemRepository.save(
                new SalesItemEntity(
                        order,
                        "회수통",
                        new BigDecimal("2"),
                        null
                )
        );

        int corrected = service.correctExistingSales();

        SalesItemEntity result = salesItemRepository
                .findById(item.getId())
                .orElseThrow();

        assertThat(corrected).isEqualTo(1);
        assertThat(result.getUnitPrice()).isEqualByComparingTo("0");
        assertThat(result.getLineAmount()).isEqualByComparingTo("0");
    }

    private SalesOrderEntity saveOrder(
            String orderNumber,
            String returnContainerPrice
    ) {
        VendorEntity vendor = vendorRepository.findByInputName("테스트거래처")
                .orElseGet(() -> vendorRepository.save(
                        new VendorEntity(
                                "테스트거래처",
                                "테스트거래처",
                                true
                        )
                ));

        return salesOrderRepository.save(new SalesOrderEntity(
                orderNumber,
                LocalDate.of(2026, 7, 1),
                vendor,
                returnContainerPrice == null
                        ? null
                        : new BigDecimal(returnContainerPrice),
                "",
                "",
                "20260701",
                5
        ));
    }
}
