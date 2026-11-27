package com.example.salesmgmt.controller;

import com.example.salesmgmt.service.VendorRegionService;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
public class VendorRegionController {
    private final VendorRegionService service;
    public VendorRegionController(VendorRegionService service){this.service=service;}
    @PostMapping("/region-sales/location")
    public VendorRegionService.Result resolve(@RequestParam Long vendorId,
        @RequestParam(required=false) String address,
        @RequestParam(required=false) String region,
        @RequestParam(required=false) BigDecimal lat,
        @RequestParam(required=false) BigDecimal lng,
        @RequestParam(defaultValue="false") boolean manual){
        return service.resolve(vendorId,address,region,lat,lng,manual);
    }
}
