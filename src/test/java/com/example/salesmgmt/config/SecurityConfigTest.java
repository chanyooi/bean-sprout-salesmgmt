package com.example.salesmgmt.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.SecurityProbeController.class)
@Import(SecurityConfig.class)
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
    void adminCanImportExcel() throws Exception {
        mockMvc.perform(
                        post("/excel/import")
                                .with(csrf())
                )
                .andExpect(status().isOk());
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
    void adminCanUpdateVendorBasePrice() throws Exception {
        mockMvc.perform(
                        post("/vendor-management/1/prices/2")
                                .with(csrf())
                )
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void workUserCanStillOpenRegularVendorManagementPage() throws Exception {
        mockMvc.perform(get("/vendor-management"))
                .andExpect(status().isOk());
    }

    @RestController
    static class SecurityProbeController {

        @PostMapping("/excel/import")
        String importExcel() {
            return "ok";
        }

        @GetMapping("/vendor-management")
        String vendorManagement() {
            return "ok";
        }

        @PostMapping("/vendor-management/{vendorId}/prices")
        String createVendorPrice(@PathVariable Long vendorId) {
            return "ok";
        }

        @PostMapping("/vendor-management/{vendorId}/prices/{priceId}")
        String updateVendorPrice(
                @PathVariable Long vendorId,
                @PathVariable Long priceId
        ) {
            return "ok";
        }
    }
}
