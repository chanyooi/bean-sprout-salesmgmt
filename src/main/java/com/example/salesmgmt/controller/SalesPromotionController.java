package com.example.salesmgmt.controller;

import com.example.salesmgmt.service.SalesManagementService;
import com.example.salesmgmt.service.SalesPromotionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/promotions")
public class SalesPromotionController {

    private final SalesPromotionService promotionService;
    private final SalesManagementService salesManagementService;

    public SalesPromotionController(
            SalesPromotionService promotionService,
            SalesManagementService salesManagementService
    ) {
        this.promotionService = promotionService;
        this.salesManagementService = salesManagementService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute(
                "promotions",
                promotionService.findAll()
        );
        model.addAttribute(
                "vendors",
                salesManagementService.findVendorOptions()
        );
        model.addAttribute(
                "today",
                LocalDate.now()
        );
        return "promotions";
    }

    @PostMapping
    public String create(
            @RequestParam Long vendorId,
            @RequestParam String itemName,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam(required = false)
            BigDecimal promotionUnitPrice,
            @RequestParam(required = false) String memo,
            RedirectAttributes redirectAttributes
    ) {
        try {
            promotionService.create(
                    vendorId,
                    itemName,
                    startDate,
                    endDate,
                    promotionUnitPrice,
                    memo
            );
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    promotionUnitPrice == null
                            ? "행사를 기록했습니다. 단가가 확정되면 입력 후 판매에 반영해주세요."
                            : "행사를 기록했습니다."
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }
        return "redirect:/promotions";
    }

    @PostMapping("/{id}/update")
    public String update(
            @PathVariable Long id,
            @RequestParam(required = false)
            BigDecimal promotionUnitPrice,
            @RequestParam(required = false) String memo,
            RedirectAttributes redirectAttributes
    ) {
        try {
            promotionService.update(
                    id,
                    promotionUnitPrice,
                    memo
            );
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "행사 단가/메모를 저장했습니다."
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }
        return "redirect:/promotions";
    }

    @PostMapping("/{id}/apply")
    public String apply(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            int count =
                    promotionService.applyToExistingSales(id);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "행사 단가를 기존 판매 "
                            + count
                            + "건에 반영했습니다."
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }
        return "redirect:/promotions";
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            promotionService.delete(id);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "행사 기록을 삭제했습니다."
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }
        return "redirect:/promotions";
    }
}
