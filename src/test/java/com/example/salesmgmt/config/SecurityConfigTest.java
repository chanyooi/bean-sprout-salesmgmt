package com.example.salesmgmt.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "USER")
    void workUserCannotImportExcel() throws Exception {
        mockMvc.perform(
                        post("/excel/import")
                                .with(csrf())
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReachImportEndpoint() throws Exception {
        mockMvc.perform(
                        post("/excel/import")
                                .with(csrf())
                )
                .andExpect(result -> assertThat(
                        result.getResponse().getStatus()
                ).isNotEqualTo(403));
    }

    @Test
    @WithMockUser(roles = "USER")
    void workUserCannotCreateVendorBasePrice() throws Exception {
        mockMvc.perform(
                        post("/vendor-management/1/prices")
                                .with(csrf())
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void workUserCannotUpdateVendorBasePrice() throws Exception {
        mockMvc.perform(
                        post("/vendor-management/1/prices/2")
                                .with(csrf())
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReachVendorBasePriceEndpoint() throws Exception {
        mockMvc.perform(
                        post("/vendor-management/1/prices/2")
                                .with(csrf())
                )
                .andExpect(result -> assertThat(
                        result.getResponse().getStatus()
                ).isNotEqualTo(403));
    }

    @Test
    @WithMockUser(roles = "USER")
    void workUserCanStillOpenRegularVendorManagementPage() throws Exception {
        mockMvc.perform(get("/vendor-management"))
                .andExpect(status().isOk());
    }
}
