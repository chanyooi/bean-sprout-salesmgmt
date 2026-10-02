package com.example.salesmgmt.controller;

import com.example.salesmgmt.repository.DailyEntryLayoutRepository;
import com.example.salesmgmt.repository.SalesItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DailyEntryLayoutControllerTest {
    @Autowired MockMvc mvc;
    @Autowired DailyEntryLayoutRepository layouts;
    @Autowired SalesItemRepository sales;
    private static final String LAYOUT = """
            {"vendorOrder":["2","1"],"hiddenVendors":["1"],
             "productOrder":["regular","cutKg"],"hiddenProducts":["cutKg"]}
            """;

    @Test void savedLayoutRendersOnNextVisitWithoutChangingSales() throws Exception {
        long originalSales = sales.count();
        mvc.perform(post("/daily-entry/layout").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(LAYOUT))
                .andExpect(status().isOk());
        assertThat(layouts.findById(1L).orElseThrow().getVendorOrder()).isEqualTo("2,1");
        String html = mvc.perform(get("/daily-entry").param("date", "2026-10-02")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("data-layout-edit", "data-entry-sequence=\"1\"", "window.dailyEntryLayout", "hiddenProducts");
        assertThat(sales.count()).isEqualTo(originalSales);
    }

    @Test void normalUsersAndMissingCsrfCannotChangeLayout() throws Exception {
        mvc.perform(post("/daily-entry/layout").with(user("worker").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(LAYOUT))
                .andExpect(status().isForbidden());
        mvc.perform(post("/daily-entry/layout").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(LAYOUT))
                .andExpect(status().isForbidden());
    }
}
