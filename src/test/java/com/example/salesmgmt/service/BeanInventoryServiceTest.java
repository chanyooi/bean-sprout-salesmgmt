package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.BeanOrigin;
import com.example.salesmgmt.domain.BeanType;
import com.example.salesmgmt.domain.BeanUsageCostResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(BeanInventoryService.class)
class BeanInventoryServiceTest {

    @Autowired
    BeanInventoryService service;

    @Test
    void 사용기록에_kg당_단가가_있으면_매입기록없이도_실제단가로_원가를_계산한다() {
        service.addUsage(
                LocalDate.of(2026, 8, 21),
                BeanType.LARGE,
                BeanOrigin.CHINA,
                new BigDecimal("2"),
                new BigDecimal("4200"),
                null
        );

        BeanUsageCostResult result = service.calculateUsageCost(YearMonth.of(2026, 8));

        assertThat(result.totalUsageBags()).isEqualByComparingTo("2");
        assertThat(result.totalUsageKg()).isEqualByComparingTo("50");
        assertThat(result.knownUsageCost()).isEqualByComparingTo("210000");
        assertThat(result.missingCostUsageCount()).isZero();
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().effectiveAveragePricePerBag())
                .isEqualByComparingTo("105000");
    }
}
