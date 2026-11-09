package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.StandardStatementSchema;
import com.example.salesmgmt.domain.StandardStatementSchema.Kind;
import com.example.salesmgmt.service.StandardStatementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/bookkeeping")
public class StandardStatementController {
    private final StandardStatementService service;

    public StandardStatementController(StandardStatementService service) { this.service = service; }

    @GetMapping
    public String page(@RequestParam(required = false) Integer year,
                       @RequestParam(defaultValue = "COST") Kind type,
                       Model model) {
        int selectedYear = year == null ? LocalDate.now().getYear() : year;
        checkYear(selectedYear);
        Map<String, BigDecimal> manual = service.entered(selectedYear);
        Map<String, BigDecimal> computed = StandardStatementSchema.evaluate(manual);
        List<AccountRow> rows = new ArrayList<>();
        int filled = 0;
        for (StandardStatementSchema.Field field : StandardStatementSchema.fields(type)) {
            String key = StandardStatementSchema.key(type, field.code());
            BigDecimal amount = computed.get(key);
            BigDecimal entered = manual.get(key);
            if (entered != null) filled++;
            rows.add(new AccountRow(field.code(), field.label(), field.indent(), field.computed(),
                    entered, amount, field.formula()));
        }
        model.addAttribute("year", selectedYear);
        model.addAttribute("type", type);
        model.addAttribute("types", Kind.values());
        model.addAttribute("rows", rows);
        model.addAttribute("filled", filled);
        model.addAttribute("fieldCount", rows.size());
        model.addAttribute("assets", computed.get("BALANCE:62"));
        model.addAttribute("liabilitiesEquity", computed.get("BALANCE:91"));
        model.addAttribute("balanceDifference",
                computed.get("BALANCE:62").subtract(computed.get("BALANCE:91")));
        model.addAttribute("netIncome", computed.get("INCOME:99"));
        model.addAttribute("manufacturingCost", computed.get("COST:42"));
        model.addAttribute("sales", computed.get("INCOME:01"));
        return "standard-bookkeeping";
    }

    @PostMapping("/standard/save")
    public String save(@RequestParam int year, @RequestParam Kind type,
                       @RequestParam Map<String, String> params,
                       RedirectAttributes flash) {
        checkYear(year);
        try {
            service.save(year, type, params);
            flash.addFlashAttribute("bookkeepingMessage",
                    type.getLabel() + " 입력 금액을 저장했습니다. 연결된 합계도 다시 계산했습니다.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("bookkeepingError", ex.getMessage() + " 입력 값은 변경되지 않았습니다.");
        }
        return "redirect:/bookkeeping?year=" + year + "&type=" + type.name();
    }

    private static void checkYear(int year) {
        if (year < 2020 || year > 2100)
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "조회 가능 연도는 2020~2100년입니다.");
    }

    public record AccountRow(String code, String label, int indent, boolean computed,
                             BigDecimal entered, BigDecimal amount, String formula) {
        public String getCode() { return code; }
        public String getLabel() { return label; }
        public int getIndent() { return indent; }
        public boolean isComputed() { return computed; }
        public BigDecimal getEntered() { return entered; }
        public BigDecimal getAmount() { return amount; }
        public String getFormula() { return formula; }
    }
}
