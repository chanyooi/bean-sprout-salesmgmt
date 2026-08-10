package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.EditableSaleRow;
import com.example.salesmgmt.service.SalesManagementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@Controller
public class SalesController {

    private final SalesManagementService salesManagementService;

    public SalesController(
            SalesManagementService salesManagementService
    ) {
        this.salesManagementService = salesManagementService;
    }

    @GetMapping("/sales")
    public String salesPage(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(defaultValue = "false") boolean missingPriceOnly,
            Model model
    ) {
        YearMonth selectedMonth =
                salesManagementService.resolveMonth(month);

        List<EditableSaleRow> allRows =
                salesManagementService.findRows(
                        selectedMonth,
                        vendorId
                );

        long missingPriceCount = allRows.stream()
                .filter(row -> row.unitPrice() == null)
                .count();

        List<EditableSaleRow> rows = missingPriceOnly
                ? allRows.stream()
                    .filter(row -> row.unitPrice() == null)
                    .toList()
                : allRows;

        BigDecimal totalAmount = rows.stream()
                .map(EditableSaleRow::lineAmount)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long orderCount = rows.stream()
                .map(EditableSaleRow::orderId)
                .distinct()
                .count();

        model.addAttribute("sales", rows);
        model.addAttribute(
                "vendors",
                salesManagementService.findVendorOptions()
        );
        model.addAttribute("selectedMonth", selectedMonth.toString());
        model.addAttribute(
                "previousMonth",
                selectedMonth.minusMonths(1).toString()
        );
        model.addAttribute(
                "nextMonth",
                selectedMonth.plusMonths(1).toString()
        );
        model.addAttribute("selectedVendorId", vendorId);
        model.addAttribute("missingPriceOnly", missingPriceOnly);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("orderCount", orderCount);
        model.addAttribute("missingPriceCount", missingPriceCount);
        model.addAttribute("allItemCount", allRows.size());

        return "sales";
    }

    @PostMapping("/sales/items/{itemId}/update")
    public String updateItem(
            @PathVariable Long itemId,
            @RequestParam BigDecimal quantity,
            @RequestParam(required = false) BigDecimal unitPrice,
            @RequestParam String month,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(defaultValue = "false") boolean missingPriceOnly,
            RedirectAttributes redirectAttributes
    ) {
        try {
            salesManagementService.updateItem(
                    itemId,
                    quantity,
                    unitPrice
            );
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "판매 품목을 수정했습니다."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return redirectToSales(
                month,
                vendorId,
                missingPriceOnly
        );
    }

    @PostMapping("/sales/items/{itemId}/delete")
    public String deleteItem(
            @PathVariable Long itemId,
            @RequestParam String month,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(defaultValue = "false") boolean missingPriceOnly,
            RedirectAttributes redirectAttributes
    ) {
        try {
            boolean orderDeleted =
                    salesManagementService.deleteItem(itemId);

            String message = orderDeleted
                    ? "마지막 품목을 삭제하여 주문도 함께 삭제했습니다."
                    : "판매 품목을 삭제했습니다.";

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    message
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return redirectToSales(
                month,
                vendorId,
                missingPriceOnly
        );
    }

    @PostMapping("/sales/orders/{orderId}/delete")
    public String deleteOrder(
            @PathVariable Long orderId,
            @RequestParam String month,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(defaultValue = "false") boolean missingPriceOnly,
            RedirectAttributes redirectAttributes
    ) {
        try {
            long deletedItemCount =
                    salesManagementService.deleteOrder(orderId);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "주문 전체를 삭제했습니다. 삭제 품목: "
                            + deletedItemCount + "건"
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return redirectToSales(
                month,
                vendorId,
                missingPriceOnly
        );
    }

    private String redirectToSales(
            String month,
            Long vendorId,
            boolean missingPriceOnly
    ) {
        String target = "redirect:/sales?month=" + month;

        if (vendorId != null) {
            target += "&vendorId=" + vendorId;
        }

        if (missingPriceOnly) {
            target += "&missingPriceOnly=true";
        }

        return target;
    }
}
