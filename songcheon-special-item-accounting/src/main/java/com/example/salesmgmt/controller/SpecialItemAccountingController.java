package com.example.salesmgmt.controller;

import com.example.salesmgmt.service.SpecialItemAccountingService;
import com.example.salesmgmt.service.SpecialItemAccountingService.SpecialItemAccountingReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
public class SpecialItemAccountingController {

    private final SpecialItemAccountingService service;

    public SpecialItemAccountingController(SpecialItemAccountingService service) {
        this.service = service;
    }

    @GetMapping("/api/special-item-accounting")
    public SpecialItemAccountingReport report(@RequestParam String month) {
        return service.report(YearMonth.parse(month));
    }
}
