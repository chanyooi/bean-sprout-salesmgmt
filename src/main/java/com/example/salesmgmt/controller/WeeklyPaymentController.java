package com.example.salesmgmt.controller;

import com.example.salesmgmt.service.WeeklyPaymentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
public class WeeklyPaymentController {

    private final WeeklyPaymentService weeklyPaymentService;

    public WeeklyPaymentController(WeeklyPaymentService weeklyPaymentService) {
        this.weeklyPaymentService = weeklyPaymentService;
    }

    @GetMapping("/payments/weekly")
    public String weeklyPayments(
            @RequestParam(required = false) String week,
            Model model
    ) {
        LocalDate weekStart = weeklyPaymentService.resolveWeekStart(week);
        WeeklyPaymentService.WeeklyReport report = weeklyPaymentService.createReport(weekStart);

        model.addAttribute("report", report);
        model.addAttribute("selectedWeek", weekStart.toString());
        model.addAttribute("previousWeek", weekStart.minusWeeks(1).toString());
        model.addAttribute("nextWeek", weekStart.plusWeeks(1).toString());
        model.addAttribute("today", LocalDate.now().toString());
        return "weekly-payments";
    }

    @PostMapping("/payments/weekly/add")
    public String addPayment(
            @RequestParam LocalDate weekStart,
            @RequestParam Long vendorId,
            @RequestParam LocalDate paymentDate,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String note,
            RedirectAttributes redirectAttributes
    ) {
        try {
            weeklyPaymentService.addPayment(
                    weekStart,
                    vendorId,
                    paymentDate,
                    amount,
                    note
            );
            redirectAttributes.addFlashAttribute("successMessage", "주별 입금 기록을 저장했습니다.");
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/payments/weekly?week=" + weekStart;
    }

    @PostMapping("/payments/weekly/{vendorId}/complete")
    public String completePayment(
            @PathVariable Long vendorId,
            @RequestParam LocalDate weekStart,
            RedirectAttributes redirectAttributes
    ) {
        try {
            BigDecimal amount = weeklyPaymentService.completeOutstanding(
                    weekStart,
                    vendorId,
                    LocalDate.now()
            );
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "주간 사용금액 " + amount.stripTrailingZeros().toPlainString() + "원을 입금 완료 처리했습니다."
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/payments/weekly?week=" + weekStart;
    }

    @PostMapping("/payments/weekly/history/{paymentId}/delete")
    public String deletePayment(
            @PathVariable Long paymentId,
            @RequestParam LocalDate weekStart,
            RedirectAttributes redirectAttributes
    ) {
        try {
            weeklyPaymentService.deletePayment(paymentId);
            redirectAttributes.addFlashAttribute("successMessage", "주별 입금 기록을 삭제했습니다.");
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/payments/weekly?week=" + weekStart;
    }
}
