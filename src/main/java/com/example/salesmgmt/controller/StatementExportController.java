package com.example.salesmgmt.controller;

import com.example.salesmgmt.service.WebStatementService;
import com.example.salesmgmt.service.StatementDeliveryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@Controller
public class StatementExportController {

    private final WebStatementService service;
    private final StatementDeliveryService deliveryService;

    public StatementExportController(
            WebStatementService service,
            StatementDeliveryService deliveryService
    ) {
        this.service = service;
        this.deliveryService = deliveryService;
    }

    @GetMapping("/statement-export")
    public String page(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(defaultValue = "false") boolean fromSendQueue,
            Model model
    ) {
        YearMonth selectedMonth =
                month == null || month.isBlank()
                        ? YearMonth.now()
                        : YearMonth.parse(month);

        model.addAttribute(
                "selectedMonth",
                selectedMonth.toString()
        );
        model.addAttribute(
                "selectedVendorId",
                vendorId
        );
        model.addAttribute(
                "vendors",
                service.vendors()
        );

        model.addAttribute(
                "recipientPhone",
                vendorId == null
                        ? null
                        : deliveryService.phoneForVendor(vendorId)
        );
        model.addAttribute(
                "managedSmsRecipient",
                vendorId != null
                        && deliveryService.isManaged(vendorId)
        );
        model.addAttribute(
                "alreadySent",
                vendorId != null
                        && deliveryService.isSent(
                                vendorId,
                                selectedMonth
                        )
        );
        model.addAttribute(
                "fromSendQueue",
                fromSendQueue
        );

        model.addAttribute(
                "statement",
                vendorId == null
                        ? null
                        : service.create(
                                selectedMonth,
                                vendorId
                        )
        );

        return "statement-export";
    }
}
