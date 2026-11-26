package com.example.salesmgmt.controller;

import com.example.salesmgmt.service.PaymentService;
import com.example.salesmgmt.repository.PersonalBankAliasRepository;
import com.example.salesmgmt.repository.PersonalBankTransactionRepository;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
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

    private final PersonalBankAliasRepository aliases;
    private final PersonalBankTransactionRepository transactions;

    public PaymentController(
            PaymentService paymentService,
            VendorManagementService vendorManagementService,
            PersonalBankAliasRepository aliases,
            PersonalBankTransactionRepository transactions
    ) {
        this.paymentService = paymentService;
        this.vendorManagementService = vendorManagementService;
        this.aliases = aliases;
        this.transactions = transactions;
    }

    @GetMapping("/payments")
    public String payments(
            @RequestParam(required = false) String month,
            Model model
    ) {
        YearMonth selectedMonth =
                paymentService.resolveMonth(month);

        var report = paymentService.createMonthlyReport(selectedMonth);
        model.addAttribute("report", report);
        var vendorIds = report.vendorRows().stream().map(row -> row.vendorId()).distinct().toList();
        var names = new LinkedHashMap<Long, LinkedHashSet<String>>();
        if (!vendorIds.isEmpty()) {
            aliases.findByVendorIdIn(vendorIds).stream()
                    .sorted(java.util.Comparator.comparing(alias -> alias.getAlias()))
                    .forEach(alias -> names.computeIfAbsent(alias.getVendorId(), key -> new LinkedHashSet<>()).add(alias.getAlias()));
            transactions.findLinkedPayerNames(vendorIds).stream()
                    .filter(row -> !((String) row[1]).isBlank())
                    .sorted(java.util.Comparator.comparing(row -> (String) row[1]))
                    .forEach(row -> names.computeIfAbsent((Long) row[0], key -> new LinkedHashSet<>()).add((String) row[1]));
        }
        var payerNames = new LinkedHashMap<Long, List<String>>();
        names.forEach((id, values) -> payerNames.put(id, List.copyOf(values)));
        model.addAttribute("payerNames", payerNames);
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
        model.addAttribute(
                "autoCompleteSummary",
                paymentService.getAutoCompleteSummary(selectedMonth)
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

    @PostMapping("/payments/complete-all")
    public String completeAllPayments(
            @RequestParam String month,
            RedirectAttributes redirectAttributes
    ) {
        try {
            YearMonth settlementMonth = YearMonth.parse(month);

            PaymentService.BulkCompleteResult result =
                    paymentService.completeAllOutstandingPayments(
                            settlementMonth,
                            LocalDate.now()
                    );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    result.vendorCount()
                            + "개 거래처 전체 입금 완료 처리했습니다. "
                            + result.totalAmount()
                                .stripTrailingZeros()
                                .toPlainString()
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

    @PostMapping("/payments/delete-auto-completions")
    public String deleteAllAutoCompletions(
            @RequestParam String month,
            RedirectAttributes redirectAttributes
    ) {
        try {
            YearMonth settlementMonth = YearMonth.parse(month);

            PaymentService.BulkDeleteResult result =
                    paymentService.deleteAllAutoCompletionPayments(
                            settlementMonth
                    );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "자동 입금 완료 기록 "
                            + result.deletedCount()
                            + "건을 삭제했습니다. "
                            + result.totalAmount()
                                .stripTrailingZeros()
                                .toPlainString()
                            + "원이 다시 미수금으로 복원됩니다."
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
