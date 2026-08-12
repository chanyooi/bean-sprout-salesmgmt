package com.example.salesmgmt.controller;

import com.example.salesmgmt.service.PaymentService;
import com.example.salesmgmt.service.VendorManagementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@Controller
public class PaymentController {

    private final PaymentService paymentService;
    private final VendorManagementService vendorManagementService;

    public PaymentController(
            PaymentService paymentService,
            VendorManagementService vendorManagementService
    ) {
        this.paymentService = paymentService;
        this.vendorManagementService = vendorManagementService;
    }

    @GetMapping("/payments")
    public String payments(
            @RequestParam(required = false) String month,
            Model model
    ) {
        YearMonth selectedMonth =
                paymentService.resolveMonth(month);

        model.addAttribute(
                "report",
                paymentService.createMonthlyReport(selectedMonth)
        );
        model.addAttribute(
                "vendors",
                vendorManagementService.findAllRows()
        );
        model.addAttribute(
                "selectedMonth",
                selectedMonth.toString()
        );
        model.addAttribute(
                "previousMonth",
                selectedMonth.minusMonths(1).toString()
        );
        model.addAttribute(
                "nextMonth",
                selectedMonth.plusMonths(1).toString()
        );
        model.addAttribute(
                "today",
                LocalDate.now().toString()
        );

        return "payments";
    }

    @PostMapping("/payments/add")
    public String addPayment(
            @RequestParam String settlementMonth,
            @RequestParam Long vendorId,
            @RequestParam LocalDate paymentDate,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String note,
            RedirectAttributes redirectAttributes
    ) {
        try {
            YearMonth month = YearMonth.parse(settlementMonth);

            paymentService.addPayment(
                    month,
                    vendorId,
                    paymentDate,
                    amount,
                    note
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "입금 기록을 저장했습니다."
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/payments?month=" + settlementMonth;
    }

    @PostMapping("/payments/{vendorId}/complete")
    public String completePayment(
            @PathVariable Long vendorId,
            @RequestParam String month,
            RedirectAttributes redirectAttributes
    ) {
        try {
            YearMonth settlementMonth = YearMonth.parse(month);

            BigDecimal completedAmount =
                    paymentService.completeOutstandingPayment(
                            settlementMonth,
                            vendorId,
                            LocalDate.now()
                    );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "입금 완료 처리했습니다. "
                            + completedAmount.stripTrailingZeros().toPlainString()
                            + "원이 자동 등록되었습니다."
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/payments?month=" + month;
    }

    @PostMapping("/payments/{paymentId}/delete")
    public String deletePayment(
            @PathVariable Long paymentId,
            @RequestParam String month,
            RedirectAttributes redirectAttributes
    ) {
        try {
            paymentService.deletePayment(paymentId);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "입금 기록을 삭제했습니다."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/payments?month=" + month;
    }
}
