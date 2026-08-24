package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.ExpenseType;
import com.example.salesmgmt.domain.MonthlyProfitReport;
import com.example.salesmgmt.service.MonthlyCostService;
import com.example.salesmgmt.service.MonthlyCustomExpenseService;
import com.example.salesmgmt.service.MonthlyExpenseService;
import com.example.salesmgmt.service.MonthlyProfitService;
import com.example.salesmgmt.service.MonthlySalesReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Controller
public class ProfitController {

    private final MonthlyProfitService monthlyProfitService;
    private final MonthlyExpenseService monthlyExpenseService;
    private final MonthlyCustomExpenseService monthlyCustomExpenseService;
    private final MonthlyCostService monthlyCostService;
    private final MonthlySalesReportService monthlySalesReportService;

    public ProfitController(
            MonthlyProfitService monthlyProfitService,
            MonthlyExpenseService monthlyExpenseService,
            MonthlyCustomExpenseService monthlyCustomExpenseService,
            MonthlyCostService monthlyCostService,
            MonthlySalesReportService monthlySalesReportService
    ) {
        this.monthlyProfitService = monthlyProfitService;
        this.monthlyExpenseService = monthlyExpenseService;
        this.monthlyCustomExpenseService = monthlyCustomExpenseService;
        this.monthlyCostService = monthlyCostService;
        this.monthlySalesReportService = monthlySalesReportService;
    }

    @GetMapping("/profit")
    public String profit(
            @RequestParam(required = false) String month,
            Model model
    ) {
        YearMonth selectedMonth = resolveMonth(month, model);
        MonthlyProfitReport report = monthlyProfitService.createReport(selectedMonth);
        Map<ExpenseType, BigDecimal> expenses = monthlyExpenseService.getExpenses(selectedMonth);

        model.addAttribute("report", report);
        model.addAttribute("expenses", expenses);
        model.addAttribute("vinylAmount", expenses.get(ExpenseType.VINYL));
        model.addAttribute("boxAmount", expenses.get(ExpenseType.BOX));
        model.addAttribute("employee1WageAmount", expenses.get(ExpenseType.EMPLOYEE_1_WAGE));
        model.addAttribute("employee2WageAmount", expenses.get(ExpenseType.EMPLOYEE_2_WAGE));
        model.addAttribute("mealAmount", expenses.get(ExpenseType.MEAL));
        model.addAttribute("rentAmount", expenses.get(ExpenseType.RENT));
        model.addAttribute("otherAmount", expenses.get(ExpenseType.OTHER));
        model.addAttribute("selectedMonth", selectedMonth.toString());
        model.addAttribute("previousMonth", selectedMonth.minusMonths(1).toString());
        model.addAttribute("nextMonth", selectedMonth.plusMonths(1).toString());
        return "profit";
    }

    @GetMapping("/profit/custom-expenses")
    @ResponseBody
    public List<MonthlyCustomExpenseService.CustomExpenseRow> customExpenses(
            @RequestParam String month
    ) {
        return monthlyCustomExpenseService.getExpenses(parseRequiredMonth(month));
    }

    @PostMapping("/profit/expenses")
    public String saveExpenses(
            @RequestParam String month,
            @RequestParam(defaultValue = "0") BigDecimal vinyl,
            @RequestParam(defaultValue = "0") BigDecimal box,
            @RequestParam(defaultValue = "0") BigDecimal employee1Wage,
            @RequestParam(defaultValue = "0") BigDecimal employee2Wage,
            @RequestParam(defaultValue = "0") BigDecimal meal,
            @RequestParam(defaultValue = "0") BigDecimal rent,
            @RequestParam(defaultValue = "0") BigDecimal other,
            @RequestParam(name = "customExpenseName", required = false) List<String> customExpenseNames,
            @RequestParam(name = "customExpenseAmount", required = false) List<BigDecimal> customExpenseAmounts,
            RedirectAttributes redirectAttributes
    ) {
        YearMonth selectedMonth;
        try {
            selectedMonth = YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            redirectAttributes.addFlashAttribute("profitError", "저장할 월 형식이 올바르지 않습니다.");
            return "redirect:/profit";
        }

        Map<ExpenseType, BigDecimal> amounts = new EnumMap<>(ExpenseType.class);
        amounts.put(ExpenseType.VINYL, vinyl);
        amounts.put(ExpenseType.BOX, box);
        amounts.put(ExpenseType.EMPLOYEE_1_WAGE, employee1Wage);
        amounts.put(ExpenseType.EMPLOYEE_2_WAGE, employee2Wage);
        amounts.put(ExpenseType.MEAL, meal);
        amounts.put(ExpenseType.RENT, rent);
        amounts.put(ExpenseType.OTHER, other);

        try {
            monthlyCostService.saveAll(
                    selectedMonth,
                    amounts,
                    customExpenseNames,
                    customExpenseAmounts
            );
            redirectAttributes.addFlashAttribute("profitMessage", "월 비용을 저장하고 손익을 다시 계산했습니다.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("profitError", exception.getMessage());
        }

        return "redirect:/profit?month=" + selectedMonth;
    }

    private YearMonth resolveMonth(String month, Model model) {
        if (month == null || month.isBlank()) {
            return monthlySalesReportService.findLatestSalesMonth().orElse(YearMonth.now());
        }

        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException exception) {
            model.addAttribute("profitError", "월 형식이 올바르지 않아 가장 최근 판매 월을 표시했습니다.");
            return monthlySalesReportService.findLatestSalesMonth().orElse(YearMonth.now());
        }
    }

    private YearMonth parseRequiredMonth(String month) {
        try {
            return YearMonth.parse(month == null ? "" : month.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("월 형식이 올바르지 않습니다.");
        }
    }
}
