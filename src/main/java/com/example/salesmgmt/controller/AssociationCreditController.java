package com.example.salesmgmt.controller;

import com.example.salesmgmt.entity.AssociationCreditTransactionEntity.TransactionType;
import com.example.salesmgmt.service.AssociationCreditService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.text.NumberFormat;

@Controller
@RequestMapping("/association-credit")
public class AssociationCreditController {

    private final AssociationCreditService associationCreditService;

    public AssociationCreditController(
            AssociationCreditService associationCreditService
    ) {
        this.associationCreditService = associationCreditService;
    }

    @GetMapping
    public String page(Model model) {
        BigDecimal balance = associationCreditService.getCurrentBalance();

        model.addAttribute("balance", balance);
        model.addAttribute("balanceState", balanceState(balance));
        model.addAttribute("displayBalance", formatWon(balance));
        model.addAttribute("balanceNote", balanceNote(balance));
        model.addAttribute("transactions", associationCreditService.getTransactions());
        model.addAttribute("today", LocalDate.now());

        return "association-credit";
    }

    @GetMapping("/api/balance")
    @ResponseBody
    public Map<String, Object> balanceApi() {
        BigDecimal balance = associationCreditService.getCurrentBalance();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("balance", balance);
        response.put("displayAmount", formatWon(balance));
        response.put("state", balanceState(balance));
        response.put("note", balanceNote(balance));
        return response;
    }

    @PostMapping("/transactions")
    public String addTransaction(
            @RequestParam LocalDate date,
            @RequestParam String type,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String note,
            RedirectAttributes redirectAttributes
    ) {
        try {
            TransactionType transactionType = TransactionType.valueOf(type.toUpperCase(Locale.ROOT));
            associationCreditService.addTransaction(date, transactionType, amount, note);

            String message = transactionType == TransactionType.CREDIT
                    ? "외상 금액을 추가했습니다."
                    : "입금 금액을 반영했습니다.";
            redirectAttributes.addFlashAttribute("associationCreditMessage", message);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute(
                    "associationCreditError",
                    ex.getMessage() == null ? "입력값을 확인해주세요." : ex.getMessage()
            );
        }

        return "redirect:/association-credit";
    }

    @PostMapping("/transactions/{id}/delete")
    public String deleteTransaction(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            associationCreditService.deleteTransaction(id);
            redirectAttributes.addFlashAttribute(
                    "associationCreditMessage",
                    "기록을 삭제했습니다."
            );
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute(
                    "associationCreditError",
                    ex.getMessage()
            );
        }
        return "redirect:/association-credit";
    }

    private static String balanceState(BigDecimal balance) {
        if (balance.signum() > 0) {
            return "debt";
        }
        if (balance.signum() < 0) {
            return "prepaid";
        }
        return "clear";
    }

    private static String balanceNote(BigDecimal balance) {
        if (balance.signum() > 0) {
            return "두채협회에 지급할 외상";
        }
        if (balance.signum() < 0) {
            return "외상보다 더 입금된 금액";
        }
        return "현재 외상 없음";
    }

    private static String formatWon(BigDecimal amount) {
        NumberFormat formatter = NumberFormat.getIntegerInstance(Locale.KOREA);
        formatter.setMaximumFractionDigits(0);
        formatter.setMinimumFractionDigits(0);
        return formatter.format(amount) + "원";
    }
}
