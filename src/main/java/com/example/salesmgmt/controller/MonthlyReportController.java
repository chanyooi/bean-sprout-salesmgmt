package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.MonthlySalesReport;
import com.example.salesmgmt.service.MonthlySalesReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Controller
public class MonthlyReportController {

    private static final DateTimeFormatter MONTH_LABEL_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREA);

    private final MonthlySalesReportService monthlySalesReportService;

    public MonthlyReportController(MonthlySalesReportService monthlySalesReportService) {
        this.monthlySalesReportService = monthlySalesReportService;
    }

    @GetMapping("/reports/monthly")
    public String monthlyReport(
            @RequestParam(name = "month", required = false) String monthValue,
            Model model
    ) {
        YearMonth selectedMonth = resolveMonth(monthValue, model);
        MonthlySalesReport report = monthlySalesReportService.createReport(selectedMonth);

        model.addAttribute("report", report);
        model.addAttribute("monthValue", selectedMonth.toString());
        model.addAttribute("monthLabel", selectedMonth.format(MONTH_LABEL_FORMATTER));
        model.addAttribute("previousMonth", selectedMonth.minusMonths(1).toString());
        model.addAttribute("nextMonth", selectedMonth.plusMonths(1).toString());

        return "monthly-report";
    }

    private YearMonth resolveMonth(String monthValue, Model model) {
        if (monthValue == null || monthValue.isBlank()) {
            return monthlySalesReportService.findLatestSalesMonth()
                    .orElse(YearMonth.now());
        }

        try {
            return YearMonth.parse(monthValue.trim());
        } catch (DateTimeParseException exception) {
            model.addAttribute(
                    "reportError",
                    "월 형식이 올바르지 않아 가장 최근 판매 월을 표시했습니다."
            );
            return monthlySalesReportService.findLatestSalesMonth()
                    .orElse(YearMonth.now());
        }
    }
}
