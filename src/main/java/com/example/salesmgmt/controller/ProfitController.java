package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.ExpenseCategory;
import com.example.salesmgmt.domain.ExpenseType;
import com.example.salesmgmt.domain.MonthlyProfitReport;
import com.example.salesmgmt.service.MonthlyExpenseItemService;
import com.example.salesmgmt.service.MonthlyExpenseService;
import com.example.salesmgmt.service.MonthlyProfitService;
import com.example.salesmgmt.service.MonthlySalesReportService;
import com.example.salesmgmt.service.SpecialItemAccountingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final MonthlyExpenseItemService monthlyExpenseItemService;
    private final MonthlySalesReportService monthlySalesReportService;
    private final SpecialItemAccountingService specialItemAccountingService;

    public ProfitController(
            MonthlyProfitService monthlyProfitService,
            MonthlyExpenseService monthlyExpenseService,
            MonthlyExpenseItemService monthlyExpenseItemService,
            MonthlySalesReportService monthlySalesReportService,
            SpecialItemAccountingService specialItemAccountingService
    ) {
        this.monthlyProfitService = monthlyProfitService;
        this.monthlyExpenseService = monthlyExpenseService;
        this.monthlyExpenseItemService = monthlyExpenseItemService;
        this.monthlySalesReportService = monthlySalesReportService;
        this.specialItemAccountingService = specialItemAccountingService;
    }

    @GetMapping("/profit")
    public String profit(
            @RequestParam(required = false) String month,
            Model model
    ) {
        YearMonth selectedMonth = resolveMonth(month, model);

        // 기존 고정 입력 비용을 새 자유 항목 구조로 최초 1회 안전하게 옮긴다.
        monthlyExpenseItemService.initializeFromLegacyIfEmpty(selectedMonth);

        MonthlyProfitReport report = monthlyProfitService.createReport(selectedMonth);
        var specialItems = specialItemAccountingService.report(selectedMonth);

        model.addAttribute("report", report);
        model.addAttribute("expenseGroups", monthlyExpenseItemService.getGroups(selectedMonth));
        model.addAttribute("expenseItems", monthlyExpenseItemService.getItems(selectedMonth));
        model.addAttribute("expenseCategories", ExpenseCategory.values());
        model.addAttribute("operatingExpenseTotal", monthlyExpenseItemService.getOperatingExpenseTotal(selectedMonth));
        model.addAttribute("tofuPurchaseCost", specialItems.tofuPurchaseCost());
        model.addAttribute("selectedMonth", selectedMonth.toString());
        model.addAttribute("previousMonth", selectedMonth.minusMonths(1).toString());
        model.addAttribute("nextMonth", selectedMonth.plusMonths(1).toString());
        return "profit";
    }

    @PostMapping("/profit/expense-items/bulk")
    public String saveExpenseItems(
            @RequestParam String month,
            @RequestParam(name = "itemId") List<Long> itemIds,
            @RequestParam(name = "itemName") List<String> itemNames,
            @RequestParam(name = "category") List<ExpenseCategory> categories,
            @RequestParam(name = "amount") List<BigDecimal> amounts,
            RedirectAttributes redirectAttributes
    ) {
        YearMonth selectedMonth = parseMonthForRedirect(month, redirectAttributes);
        if (selectedMonth == null) {
            return "redirect:/profit";
        }

        try {
            monthlyExpenseItemService.updateItems(
                    selectedMonth,
                    itemIds,
                    itemNames,
                    categories,
                    amounts
            );
            redirectAttributes.addFlashAttribute("profitMessage", "월 운영비를 저장하고 이익을 다시 계산했습니다.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("profitError", exception.getMessage());
        }
        return "redirect:/profit?month=" + selectedMonth;
    }

    @PostMapping("/profit/expense-items")
    public String addExpenseItem(
            @RequestParam String month,
            @RequestParam ExpenseCategory category,
            @RequestParam String itemName,
            @RequestParam BigDecimal amount,
            RedirectAttributes redirectAttributes
    ) {
        YearMonth selectedMonth = parseMonthForRedirect(month, redirectAttributes);
        if (selectedMonth == null) {
            return "redirect:/profit";
        }

        try {
            monthlyExpenseItemService.addItem(selectedMonth, category, itemName, amount);
            redirectAttributes.addFlashAttribute("profitMessage", "비용 항목을 추가했습니다.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("profitError", exception.getMessage());
        }
        return "redirect:/profit?month=" + selectedMonth;
    }

    @PostMapping("/profit/expense-items/{id}/delete")
    public String deleteExpenseItem(
            @PathVariable Long id,
            @RequestParam String month,
            RedirectAttributes redirectAttributes
    ) {
        YearMonth selectedMonth = parseMonthForRedirect(month, redirectAttributes);
        if (selectedMonth == null) {
            return "redirect:/profit";
        }

        try {
            monthlyExpenseItemService.deleteItem(selectedMonth, id);
            redirectAttributes.addFlashAttribute("profitMessage", "비용 항목을 삭제했습니다.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("profitError", exception.getMessage());
        }
        return "redirect:/profit?month=" + selectedMonth;
    }

    @PostMapping("/profit/expense-items/copy-previous")
    public String copyPreviousMonthExpenses(
            @RequestParam String month,
            RedirectAttributes redirectAttributes
    ) {
        YearMonth selectedMonth = parseMonthForRedirect(month, redirectAttributes);
        if (selectedMonth == null) {
            return "redirect:/profit";
        }

        try {
            monthlyExpenseItemService.copyPreviousMonth(selectedMonth);
            redirectAttributes.addFlashAttribute(
                    "profitMessage",
                    selectedMonth.minusMonths(1) + " 비용을 그대로 불러왔습니다. 금액을 확인한 뒤 저장해주세요."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("profitError", exception.getMessage());
        }
        return "redirect:/profit?month=" + selectedMonth;
    }

    /**
     * 이전 화면/북마크 호환을 위해 기존 고정 입력 POST는 유지한다.
     * 새 화면에서는 자유 항목 API를 사용한다.
     */
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
            RedirectAttributes redirectAttributes
    ) {
        YearMonth selectedMonth = parseMonthForRedirect(month, redirectAttributes);
        if (selectedMonth == null) {
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
            monthlyExpenseService.saveExpenses(selectedMonth, amounts);
            redirectAttributes.addFlashAttribute("profitMessage", "기존 월 비용 데이터를 저장했습니다.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("profitError", exception.getMessage());
        }

        return "redirect:/profit?month=" + selectedMonth;
    }

    private YearMonth parseMonthForRedirect(String month, RedirectAttributes redirectAttributes) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            redirectAttributes.addFlashAttribute("profitError", "저장할 월 형식이 올바르지 않습니다.");
            return null;
        }
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
}
