package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.MonthlyReceivableReport;
import com.example.salesmgmt.service.PaymentService;
import com.example.salesmgmt.service.StatementDeliveryService;
import com.example.salesmgmt.service.WebStatementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.YearMonth;

@Controller
public class StatementExportController {

    private static final YearMonth RECEIVABLE_START_MONTH = YearMonth.of(2026, 7);

    private final WebStatementService service;
    private final StatementDeliveryService deliveryService;
    private final PaymentService paymentService;

    public StatementExportController(
            WebStatementService service,
            StatementDeliveryService deliveryService,
            PaymentService paymentService
    ) {
        this.service = service;
        this.deliveryService = deliveryService;
        this.paymentService = paymentService;
    }

    @GetMapping("/statement-export")
    public String page(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(defaultValue = "false") boolean fromSendQueue,
            @RequestParam(defaultValue = "false") boolean includeReceivable,
            Model model
    ) {
        YearMonth selectedMonth =
                month == null || month.isBlank()
                        ? YearMonth.now()
                        : YearMonth.parse(month);

        var statement = vendorId == null
                ? null
                : service.create(selectedMonth, vendorId);

        BigDecimal previousReceivable = vendorId == null
                ? BigDecimal.ZERO
                : previousReceivable(vendorId, selectedMonth);

        if (previousReceivable.signum() <= 0) {
            includeReceivable = false;
        }

        BigDecimal finalBillingAmount = statement == null
                ? BigDecimal.ZERO
                : statement.totalAmount().add(
                        includeReceivable ? previousReceivable : BigDecimal.ZERO
                );

        model.addAttribute("selectedMonth", selectedMonth.toString());
        model.addAttribute("selectedVendorId", vendorId);
        model.addAttribute("vendors", service.vendors());

        model.addAttribute(
                "recipientPhone",
                vendorId == null
                        ? null
                        : deliveryService.phoneForVendor(vendorId)
        );
        model.addAttribute(
                "managedSmsRecipient",
                vendorId != null
                        && deliveryService.isManaged(vendorId)
        );
        model.addAttribute(
                "alreadySent",
                vendorId != null
                        && deliveryService.isSent(vendorId, selectedMonth)
        );
        model.addAttribute("fromSendQueue", fromSendQueue);
        model.addAttribute("statement", statement);
        model.addAttribute("previousReceivable", previousReceivable);
        model.addAttribute("includeReceivable", includeReceivable);
        model.addAttribute("finalBillingAmount", finalBillingAmount);

        return "statement-export";
    }

    private BigDecimal previousReceivable(Long vendorId, YearMonth selectedMonth) {
        if (!selectedMonth.isAfter(RECEIVABLE_START_MONTH)) {
            return BigDecimal.ZERO;
        }

        BigDecimal carried = BigDecimal.ZERO;
        for (YearMonth cursor = RECEIVABLE_START_MONTH;
             cursor.isBefore(selectedMonth);
             cursor = cursor.plusMonths(1)) {

            MonthlyReceivableReport report = paymentService.createMonthlyReport(cursor);
            BigDecimal monthOutstanding = report.vendorRows().stream()
                    .filter(row -> vendorId.equals(row.vendorId()))
                    .map(MonthlyReceivableReport.VendorRow::outstandingAmount)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);

            carried = carried.add(monthOutstanding);
        }

        return carried.signum() > 0 ? carried.stripTrailingZeros() : BigDecimal.ZERO;
    }
}
