package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.BeanCatalog;
import com.example.salesmgmt.domain.BeanInventoryView;
import com.example.salesmgmt.domain.BeanOrigin;
import com.example.salesmgmt.domain.BeanType;
import com.example.salesmgmt.domain.BeanUsageCostResult;
import com.example.salesmgmt.service.BeanInventoryService;
import org.springframework.format.annotation.DateTimeFormat;
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
public class InventoryController {

    private final BeanInventoryService beanInventoryService;

    public InventoryController(BeanInventoryService beanInventoryService) {
        this.beanInventoryService = beanInventoryService;
    }

    @GetMapping("/inventory")
    public String inventory(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate asOf,
            @RequestParam(required = false) String month,
            Model model
    ) {
        BeanInventoryView inventory = beanInventoryService.getInventory(asOf);
        YearMonth selectedMonth = parseMonth(month);
        BeanUsageCostResult usageCost = beanInventoryService.calculateUsageCost(selectedMonth);

        model.addAttribute("inventory", inventory);
        model.addAttribute("usageCost", usageCost);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("previousMonth", selectedMonth.minusMonths(1));
        model.addAttribute("nextMonth", selectedMonth.plusMonths(1));
        model.addAttribute("beanTypes", BeanType.values());
        model.addAttribute("beanOrigins", BeanOrigin.values());
        model.addAttribute("allowedCombinations", BeanCatalog.ALLOWED_COMBINATIONS);
        model.addAttribute("today", LocalDate.now());
        return "inventory";
    }

    @PostMapping("/inventory/purchases")
    public String addPurchase(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseDate,
            @RequestParam BeanType beanType,
            @RequestParam BeanOrigin origin,
            @RequestParam BigDecimal bagCount,
            @RequestParam BigDecimal unitPricePerBag,
            @RequestParam(required = false) String note,
            RedirectAttributes redirectAttributes
    ) {
        try {
            beanInventoryService.addPurchase(
                    purchaseDate,
                    beanType,
                    origin,
                    bagCount,
                    unitPricePerBag,
                    note
            );
            redirectAttributes.addFlashAttribute("inventoryMessage", "콩 매입 기록을 저장했습니다.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("inventoryError", exception.getMessage());
        }
        return "redirect:/inventory";
    }

    @PostMapping("/inventory/usages")
    public String addUsage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate usageDate,
            @RequestParam BeanType beanType,
            @RequestParam BeanOrigin origin,
            @RequestParam BigDecimal bagCount,
            @RequestParam(required = false) String note,
            RedirectAttributes redirectAttributes
    ) {
        try {
            beanInventoryService.addUsage(
                    usageDate,
                    beanType,
                    origin,
                    bagCount,
                    note
            );
            redirectAttributes.addFlashAttribute("inventoryMessage", "콩 사용량을 저장했습니다.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("inventoryError", exception.getMessage());
        }
        return "redirect:/inventory?month=" + YearMonth.from(usageDate);
    }

    @PostMapping("/inventory/purchases/{purchaseId}/delete")
    public String deletePurchase(
            @PathVariable Long purchaseId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            beanInventoryService.deletePurchase(purchaseId);
            redirectAttributes.addFlashAttribute("inventoryMessage", "매입 기록을 삭제했습니다.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("inventoryError", exception.getMessage());
        }
        return "redirect:/inventory";
    }

    @PostMapping("/inventory/usages/{usageId}/delete")
    public String deleteUsage(
            @PathVariable Long usageId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            beanInventoryService.deleteUsage(usageId);
            redirectAttributes.addFlashAttribute("inventoryMessage", "사용 기록을 삭제했습니다.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("inventoryError", exception.getMessage());
        }
        return "redirect:/inventory";
    }

    @PostMapping("/inventory/threshold")
    public String updateThreshold(
            @RequestParam BeanType beanType,
            @RequestParam BeanOrigin origin,
            @RequestParam BigDecimal thresholdBags,
            RedirectAttributes redirectAttributes
    ) {
        try {
            beanInventoryService.updateThreshold(beanType, origin, thresholdBags);
            redirectAttributes.addFlashAttribute("inventoryMessage", "재고 부족 기준을 변경했습니다.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("inventoryError", exception.getMessage());
        }
        return "redirect:/inventory";
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }

        try {
            return YearMonth.parse(month.trim());
        } catch (RuntimeException ignored) {
            return YearMonth.now();
        }
    }
}
