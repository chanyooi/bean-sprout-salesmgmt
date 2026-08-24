package com.example.salesmgmt.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(MonthlyCustomExpenseService.class)
class MonthlyCustomExpenseServiceTest {

    @Autowired
    MonthlyCustomExpenseService service;

    @Test
    void 전기세와_대출이자같은_추가비용을_월별로_저장하고_다시_불러온다() {
        YearMonth month = YearMonth.of(2026, 8);

        service.replaceExpenses(
                month,
                List.of("전기세", "땅 대출이자"),
                List.of(new BigDecimal("1000000"), new BigDecimal("750000"))
        );

        List<MonthlyCustomExpenseService.CustomExpenseRow> rows = service.getExpenses(month);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).name()).isEqualTo("전기세");
        assertThat(rows.get(0).amount()).isEqualByComparingTo("1000000");
        assertThat(rows.get(1).name()).isEqualTo("땅 대출이자");
        assertThat(rows.get(1).amount()).isEqualByComparingTo("750000");
    }
}
