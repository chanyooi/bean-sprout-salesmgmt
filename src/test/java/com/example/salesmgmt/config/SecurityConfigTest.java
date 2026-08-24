package com.example.salesmgmt.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void workUserCannotImportExcel() throws Exception {
        mockMvc.perform(
                        post("/excel/import")
                                .with(user("work").roles("USER"))
                                .with(csrf())
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReachImportEndpoint() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]
        );

        mockMvc.perform(
                        multipart("/excel/import")
                                .file(file)
                                .with(user("admin").roles("ADMIN"))
                                .with(csrf())
                )
                .andExpect(result -> assertThat(
                        result.getResponse().getStatus()
                ).isNotEqualTo(403));
    }

    @Test
    void workUserCannotCreateVendorBasePrice() throws Exception {
        mockMvc.perform(
                        post("/vendor-management/1/prices")
                                .with(user("work").roles("USER"))
                                .with(csrf())
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void workUserCannotUpdateVendorBasePrice() throws Exception {
        mockMvc.perform(
                        post("/vendor-management/1/prices/2")
                                .with(user("work").roles("USER"))
                                .with(csrf())
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReachVendorBasePriceEndpoint() throws Exception {
        mockMvc.perform(
                        post("/vendor-management/1/prices/2")
                                .with(user("admin").roles("ADMIN"))
                                .with(csrf())
                )
                .andExpect(result -> assertThat(
                        result.getResponse().getStatus()
                ).isNotEqualTo(403));
    }

    @Test
    void workUserCanStillOpenRegularVendorManagementPage() throws Exception {
        mockMvc.perform(
                        get("/vendor-management")
                                .with(user("work").roles("USER"))
                )
                .andExpect(status().isOk());
    }
}
