package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.PaymentCycle;
import com.example.salesmgmt.domain.RouteCode;
import com.example.salesmgmt.domain.VendorManagementRow;
import com.example.salesmgmt.service.VendorManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class VendorProfileManagementController {

    private final VendorManagementService vendorManagementService;

    public VendorProfileManagementController(VendorManagementService vendorManagementService) {
        this.vendorManagementService = vendorManagementService;
    }

    @GetMapping("/vendor-management/{vendorId}/profile.json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> profile(@PathVariable Long vendorId) {
        VendorManagementRow row = findRow(vendorId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vendorId", row.vendorId());
        body.put("vendorName", row.vendorName());
        body.put("active", row.active());
        body.put("routeCode", row.routeCode().name());
        body.put("routeLabel", row.routeCode().getLabel());
        body.put("routeOrder", row.routeOrder());
        body.put("address", row.address());
        body.put("phone", row.phone());
        body.put("paymentCycle", row.paymentCycle().name());
        body.put("paymentCycleLabel", row.paymentCycle().getLabel());
        body.put("memo", row.memo());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/vendor-management/profile/update")
    public String update(
            @RequestParam Long vendorId,
            @RequestParam(defaultValue = "false") boolean active,
            @RequestParam RouteCode routeCode,
            @RequestParam(required = false) Integer routeOrder,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String phone,
            @RequestParam PaymentCycle paymentCycle,
            @RequestParam(required = false) String memo,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) String returnTo,
            RedirectAttributes redirectAttributes
    ) {
        try {
            vendorManagementService.updateProfile(
                    vendorId,
                    active,
                    routeCode,
                    routeOrder,
                    address,
                    phone,
                    paymentCycle,
                    memo,
                    latitude,
                    longitude
            );
            redirectAttributes.addFlashAttribute("successMessage", "거래처 기본정보를 저장했습니다.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }

        if (returnTo != null && returnTo.startsWith("/vendor-management")) {
            return "redirect:" + returnTo;
        }
        return "redirect:/vendor-management";
    }

    private VendorManagementRow findRow(Long vendorId) {
        return vendorManagementService.findAllRows().stream()
                .filter(row -> row.vendorId().equals(vendorId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("거래처를 찾을 수 없습니다."));
    }
}
