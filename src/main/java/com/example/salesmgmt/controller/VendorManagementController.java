package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.PaymentCycle;
import com.example.salesmgmt.domain.RouteCode;
import com.example.salesmgmt.service.VendorManagementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
public class VendorManagementController {

    private final VendorManagementService vendorManagementService;

    public VendorManagementController(
            VendorManagementService vendorManagementService
    ) {
        this.vendorManagementService = vendorManagementService;
    }

    @GetMapping("/vendors")
    public String vendors(
            @RequestParam(required = false) RouteCode route,
            Model model
    ) {
        RouteCode selectedRoute =
                route == null || route == RouteCode.NONE
                        ? RouteCode.A
                        : route;

        model.addAttribute(
                "vendors",
                vendorManagementService.findAllRows()
        );
        model.addAttribute("routeCodes", RouteCode.values());
        model.addAttribute(
                "paymentCycles",
                PaymentCycle.values()
        );
        model.addAttribute(
                "routeSummary",
                vendorManagementService.getRouteSummary()
        );
        model.addAttribute(
                "selectedRoute",
                selectedRoute
        );

        return "vendors";
    }

    @PostMapping("/vendors/update")
    public String updateVendor(
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

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    routeCode == RouteCode.NONE
                            ? "거래처 정보를 저장했습니다."
                            : "거래처 정보를 저장했습니다. 배송순서는 코스 목록에 자동 반영됐습니다."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        if (returnTo != null && returnTo.startsWith("/vendor-management")) {
            return "redirect:" + returnTo;
        }

        if (routeCode != null && routeCode != RouteCode.NONE) {
            return "redirect:/vendors?route=" + routeCode.name();
        }

        return "redirect:/vendors";
    }

    @PostMapping("/vendors/routes/reorder")
    public String reorderRoute(
            @RequestParam RouteCode routeCode,
            @RequestParam(required = false) String vendorIds,
            RedirectAttributes redirectAttributes
    ) {
        try {
            vendorManagementService.reorderRoute(
                    routeCode,
                    parseVendorIds(vendorIds)
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    routeCode.getLabel()
                            + " 배송순서를 자동 저장했습니다."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/vendors?route=" + routeCode.name();
    }

    private List<Long> parseVendorIds(String vendorIds) {
        List<Long> result = new ArrayList<>();

        if (vendorIds == null || vendorIds.isBlank()) {
            return result;
        }

        for (String token : vendorIds.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            try {
                result.add(Long.parseLong(trimmed));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "배송순서 데이터가 올바르지 않습니다."
                );
            }
        }

        return result;
    }
}
