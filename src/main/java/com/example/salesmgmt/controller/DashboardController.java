package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.BeanInventoryView;
import com.example.salesmgmt.domain.DailySalesCalendarView;
import com.example.salesmgmt.domain.MonthlyProfitReport;
import com.example.salesmgmt.domain.MonthlyReceivableReport;
import com.example.salesmgmt.domain.MonthlySalesReport;
import com.example.salesmgmt.service.BeanInventoryService;
import com.example.salesmgmt.service.DailySalesCalendarService;
import com.example.salesmgmt.service.MonthlyProfitService;
import com.example.salesmgmt.service.MonthlySalesReportService;
import com.example.salesmgmt.service.PaymentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.YearMonth;

@Controller
public class DashboardController {

    private final MonthlySalesReportService monthlySalesReportService;
    private final MonthlyProfitService monthlyProfitService;
    private final PaymentService paymentService;
    private final BeanInventoryService beanInventoryService;
    private final DailySalesCalendarService dailySalesCalendarService;

    public DashboardController(
            MonthlySalesReportService monthlySalesReportService,
            MonthlyProfitService monthlyProfitService,
            PaymentService paymentService,
            BeanInventoryService beanInventoryService,
            DailySalesCalendarService dailySalesCalendarService
    ) {
        this.monthlySalesReportService = monthlySalesReportService;
        this.monthlyProfitService = monthlyProfitService;
        this.paymentService = paymentService;
        this.beanInventoryService = beanInventoryService;
        this.dailySalesCalendarService = dailySalesCalendarService;
    }

    @GetMapping("/")
    public String dashboard(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String date,
            Model model
    ) {
        YearMonth selectedMonth = month == null || month.isBlank()
                ? monthlySalesReportService.findLatestSalesMonth().orElse(YearMonth.now())
                : YearMonth.parse(month);

        // 같은 월 판매자료는 한 번만 DB에서 읽고 이익/미수금 계산에 재사용한다.
        MonthlySalesReport sales = monthlySalesReportService.createReport(selectedMonth);
        MonthlyProfitReport profit = monthlyProfitService.createReport(selectedMonth, sales);
        MonthlyReceivableReport receivables = paymentService.createMonthlyReport(
                selectedMonth,
                sales
        );

        BeanInventoryView inventory = beanInventoryService.getInventory(LocalDate.now());
        DailySalesCalendarView calendar = dailySalesCalendarService.create(
                selectedMonth.toString(),
                date
        );

        model.addAttribute("selectedMonth", selectedMonth.toString());
        model.addAttribute("previousMonth", selectedMonth.minusMonths(1).toString());
        model.addAttribute("nextMonth", selectedMonth.plusMonths(1).toString());
        model.addAttribute("sales", sales);
        model.addAttribute("profit", profit);
        model.addAttribute("receivables", receivables);
        model.addAttribute("inventory", inventory);
        model.addAttribute("calendar", calendar);

        return "dashboard";
    }
}
