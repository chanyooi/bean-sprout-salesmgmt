package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.PaymentCycle;
import com.example.salesmgmt.domain.RouteCode;
import com.example.salesmgmt.service.VendorManagementService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = VendorHubController.class)
public class VendorManagementModelAdvice {

    private final VendorManagementService vendorManagementService;

    public VendorManagementModelAdvice(VendorManagementService vendorManagementService) {
        this.vendorManagementService = vendorManagementService;
    }

    @ModelAttribute("vendorProfiles")
    public Object vendorProfiles() {
        return vendorManagementService.findAllRows();
    }

    @ModelAttribute("routeCodes")
    public RouteCode[] routeCodes() {
        return RouteCode.values();
    }

    @ModelAttribute("paymentCycles")
    public PaymentCycle[] paymentCycles() {
        return PaymentCycle.values();
    }
}
