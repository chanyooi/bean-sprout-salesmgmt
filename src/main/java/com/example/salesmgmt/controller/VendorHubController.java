package com.example.salesmgmt.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class VendorHubController {

    @GetMapping("/vendor-management")
    public String vendorManagement() {
        return "vendor-management";
    }
}
