package com.example.salesmgmt.controller;

import com.example.salesmgmt.service.SalesManagementService;
import com.example.salesmgmt.service.StatementDeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;

@Controller
public class StatementSendController {

    private final StatementDeliveryService deliveryService;
    private final SalesManagementService salesManagementService;

    public StatementSendController(
            StatementDeliveryService deliveryService,
            SalesManagementService salesManagementService
    ) {
        this.deliveryService = deliveryService;
        this.salesManagementService = salesManagementService;
    }

    @GetMapping("/statement-send")
    public String page(
            @RequestParam(required = false) String month,
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
                "queue",
                deliveryService.queue(selectedMonth)
        );
        model.addAttribute(
                "vendors",
                salesManagementService.findVendorOptions()
        );

        return "statement-send";
    }

    @PostMapping("/statement-send/settings")
    public String saveSetting(
            @RequestParam Long vendorId,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String memo,
            @RequestParam(required = false) String month,
            RedirectAttributes redirectAttributes
    ) {
        deliveryService.upsert(
                vendorId,
                phone,
                memo
        );

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "문자 명세서 대상을 저장했습니다."
        );

        return redirect(month);
    }

    @PostMapping("/statement-send/settings/remove")
    public String removeSetting(
            @RequestParam Long vendorId,
            @RequestParam(required = false) String month,
            RedirectAttributes redirectAttributes
    ) {
        deliveryService.remove(vendorId);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "문자 명세서 대상에서 제외했습니다."
        );

        return redirect(month);
    }

    @PostMapping("/statement-send/mark-sent")
    @ResponseBody
    public ResponseEntity<String> markSent(
            @RequestParam Long vendorId,
            @RequestParam String month
    ) {
        deliveryService.markSent(
                vendorId,
                YearMonth.parse(month)
        );

        return ResponseEntity.ok("OK");
    }

    @PostMapping("/statement-send/mark-unsent")
    public String markUnsent(
            @RequestParam Long vendorId,
            @RequestParam String month,
            RedirectAttributes redirectAttributes
    ) {
        deliveryService.markUnsent(
                vendorId,
                YearMonth.parse(month)
        );

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "미발송 상태로 되돌렸습니다."
        );

        return redirect(month);
    }

    private String redirect(String month) {
        if (month == null || month.isBlank()) {
            return "redirect:/statement-send";
        }

        return "redirect:/statement-send?month="
                + month;
    }
}
