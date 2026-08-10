package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.BeanInventoryView;
import com.example.salesmgmt.domain.MonthlyProfitReport;
import com.example.salesmgmt.domain.MonthlyReceivableReport;
import com.example.salesmgmt.domain.MonthlySalesReport;
import com.example.salesmgmt.service.BeanInventoryService;
import com.example.salesmgmt.service.MonthlyProfitService;
import com.example.salesmgmt.service.MonthlySalesReportService;
import com.example.salesmgmt.service.PaymentService;
import com.example.salesmgmt.service.VendorManagementService;
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
    private final VendorManagementService vendorManagementService;

    public DashboardController(
            MonthlySalesReportService monthlySalesReportService,
            MonthlyProfitService monthlyProfitService,
            PaymentService paymentService,
            BeanInventoryService beanInventoryService,
            VendorManagementService vendorManagementService
    ) {
        this.monthlySalesReportService = monthlySalesReportService;
        this.monthlyProfitService = monthlyProfitService;
        this.paymentService = paymentService;
        this.beanInventoryService = beanInventoryService;
        this.vendorManagementService = vendorManagementService;
    }

    @GetMapping("/")
    public String dashboard(
            @RequestParam(required = false) String month,
            Model model
    ) {
        YearMonth selectedMonth = month == null || month.isBlank()
                ? monthlySalesReportService
                    .findLatestSalesMonth()
                    .orElse(YearMonth.now())
                : YearMonth.parse(month);

        MonthlySalesReport sales =
                monthlySalesReportService.createReport(selectedMonth);
        MonthlyProfitReport profit =
                monthlyProfitService.createReport(selectedMonth);
        MonthlyReceivableReport receivables =
                paymentService.createMonthlyReport(selectedMonth);
        BeanInventoryView inventory =
                beanInventoryService.getInventory(LocalDate.now());

        model.addAttribute("selectedMonth", selectedMonth.toString());
        model.addAttribute(
                "previousMonth",
                selectedMonth.minusMonths(1).toString()
        );
        model.addAttribute(
                "nextMonth",
                selectedMonth.plusMonths(1).toString()
        );
        model.addAttribute("sales", sales);
        model.addAttribute("profit", profit);
        model.addAttribute("receivables", receivables);
        model.addAttribute("inventory", inventory);
        model.addAttribute(
                "lowStockRows",
                inventory.stockRows().stream()
                        .filter(row -> row.lowStock())
                        .toList()
        );
        model.addAttribute(
                "routeSummary",
                vendorManagementService.getRouteSummary()
        );

        return "dashboard";
    }
}
