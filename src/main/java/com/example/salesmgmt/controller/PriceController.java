package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.PriceImportResult;
import com.example.salesmgmt.domain.PriceSaveResult;
import com.example.salesmgmt.service.PriceManagementService;
import com.example.salesmgmt.service.PriceTemplateImportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
public class PriceController {

    private static final int PREVIEW_LIMIT = 600;

    private final PriceTemplateImportService priceTemplateImportService;
    private final PriceManagementService priceManagementService;

    public PriceController(
            PriceTemplateImportService priceTemplateImportService,
            PriceManagementService priceManagementService
    ) {
        this.priceTemplateImportService = priceTemplateImportService;
        this.priceManagementService = priceManagementService;
    }

    @GetMapping("/prices")
    public String pricesPage(
            @RequestParam(required = false) Long vendorId,
            Model model
    ) {
        populatePricePage(model, vendorId);
        return "prices";
    }

    @PostMapping("/prices/template/import")
    public String importTemplate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "preview") String action,
            Model model
    ) {
        try {
            PriceImportResult result =
                    priceTemplateImportService.importTemplate(file);

            model.addAttribute("importResult", result);
            model.addAttribute(
                    "previewRows",
                    result.rows().stream().limit(PREVIEW_LIMIT).toList()
            );
            model.addAttribute("previewLimit", PREVIEW_LIMIT);

            if ("save".equals(action)) {
                if (result.errorCount() > 0) {
                    model.addAttribute(
                            "priceError",
                            "단가 추출 오류가 있어 저장하지 않았습니다."
                    );
                } else if (result.rows().isEmpty()) {
                    model.addAttribute(
                            "priceError",
                            "저장할 단가가 없습니다."
                    );
                } else {
                    PriceSaveResult saveResult =
                            priceManagementService.saveImportedPrices(result.rows());
                    model.addAttribute("priceSaveResult", saveResult);
                }
            }
        } catch (IllegalArgumentException exception) {
            model.addAttribute("priceError", exception.getMessage());
        }

        populatePricePage(model, null);
        return "prices";
    }

    @PostMapping("/prices/update")
    public String updatePrice(
            @RequestParam Long priceId,
            @RequestParam Long vendorId,
            @RequestParam BigDecimal unitPrice,
            RedirectAttributes redirectAttributes
    ) {
        try {
            int applied = priceManagementService.updatePrice(priceId, unitPrice);
            redirectAttributes.addFlashAttribute(
                    "priceMessage",
                    "단가를 수정했습니다. 기존 미단가 판매 "
                            + applied
                            + "건에도 적용했습니다."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "priceError",
                    exception.getMessage()
            );
        }

        return "redirect:/prices?vendorId=" + vendorId;
    }

    @PostMapping("/prices/create")
    public String createPrice(
            @RequestParam Long vendorId,
            @RequestParam String itemName,
            @RequestParam BigDecimal unitPrice,
            RedirectAttributes redirectAttributes
    ) {
        try {
            int applied = priceManagementService.createOrUpdatePrice(
                    vendorId,
                    itemName,
                    unitPrice
            );
            redirectAttributes.addFlashAttribute(
                    "priceMessage",
                    "단가를 저장했습니다. 기존 미단가 판매 "
                            + applied
                            + "건에도 적용했습니다."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "priceError",
                    exception.getMessage()
            );
        }

        return "redirect:/prices?vendorId=" + vendorId;
    }

    private void populatePricePage(Model model, Long requestedVendorId) {
        var vendors = priceManagementService.findVendors();

        Long selectedVendorId = requestedVendorId;
        if (selectedVendorId == null && !vendors.isEmpty()) {
            selectedVendorId = vendors.getFirst().id();
        }

        model.addAttribute("vendors", vendors);
        model.addAttribute("selectedVendorId", selectedVendorId);
        model.addAttribute(
                "prices",
                priceManagementService.findPrices(selectedVendorId)
        );
        model.addAttribute(
                "missingItems",
                priceManagementService.findMissingItems(selectedVendorId)
        );
    }
}
