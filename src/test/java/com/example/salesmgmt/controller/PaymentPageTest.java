package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.PaymentCycle;
import com.example.salesmgmt.entity.*;
import com.example.salesmgmt.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentPageTest {
    @Autowired MockMvc mvc;
    @Autowired VendorRepository vendors;
    @Autowired VendorProfileRepository profiles;
    @Autowired SalesOrderRepository orders;
    @Autowired SalesItemRepository items;
    @Autowired WeeklyPaymentRepository payments;

    @Test void rendersWeeklyPaymentWithoutMonthlyDeleteAction() throws Exception {
        var v = vendors.save(new VendorEntity("weekly-page", "weekly-page", true));
        var profile = new VendorProfileEntity(v);
        profile.update(true, null, null, null, null, PaymentCycle.WEEKLY, null, null, null);
        profiles.save(profile);
        var o = orders.save(new SalesOrderEntity("20260803-997", LocalDate.parse("2026-08-03"), v, null, "", "", "test", 5));
        items.save(new SalesItemEntity(o, "일반콩나물", BigDecimal.TEN, new BigDecimal("1000")));
        payments.save(new WeeklyPaymentEntity(v, LocalDate.parse("2026-08-02"), LocalDate.parse("2026-08-08"), new BigDecimal("5000"), "test"));
        var response = mvc.perform(get("/payments").param("month", "2026-08").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse();
        String html = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("주별 내역에서 관리", "weekly-page", "5,000원");
        assertThat(html).doesNotContain("/payments/null/delete", "action=\"/payments/complete-all\"");
    }
}
