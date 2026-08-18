package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.StatementDeliveryMethod;
import com.example.salesmgmt.repository.VendorRepository;
import com.example.salesmgmt.service.PriceManagementService;
import com.example.salesmgmt.service.SalesManagementService;
import com.example.salesmgmt.service.VendorDetailService;
import com.example.salesmgmt.service.VendorManagementService;
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

@Controller
public class VendorHubController {

    private final PriceManagementService priceManagementService;
    private final SalesManagementService salesManagementService;
    private final VendorManagementService vendorManagementService;
    private final VendorDetailService vendorDetailService;
    private final VendorRepository vendorRepository;

    public VendorHubController(
            PriceManagementService priceManagementService,
            SalesManagementService salesManagementService,
            VendorManagementService vendorManagementService,
            VendorDetailService vendorDetailService,
            VendorRepository vendorRepository
    ) {
        this.priceManagementService = priceManagementService;
        this.salesManagementService = salesManagementService;
        this.vendorManagementService = vendorManagementService;
        this.vendorDetailService = vendorDetailService;
        this.vendorRepository = vendorRepository;
    }

    @GetMapping("/vendor-management")
    public String vendorManagement(Model model) {
        model.addAttribute(
                "vendors",
                priceManagementService.findVendors()
        );
        return "vendor-management";
    }

    @GetMapping("/vendor-management/{vendorId}")
    public String vendorDetail(
            @PathVariable Long vendorId,
            @RequestParam(required = false) String month,
            Model model
    ) {
        YearMonth selectedMonth = resolveMonth(month);
        var detail = vendorDetailService.load(vendorId, selectedMonth);

        var profile = vendorManagementService.findAllRows().stream()
                .filter(row -> row.vendorId().equals(vendorId))
                .findFirst()
                .orElse(null);

        var vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("거래처를 찾을 수 없습니다."));

        model.addAttribute("detail", detail);
        model.addAttribute("profile", profile);
        model.addAttribute("statementDeliveryMethods", StatementDeliveryMethod.values());
        model.addAttribute("statementDeliveryMethod", vendor.getStatementDeliveryMethod());
        model.addAttribute(
                "prices",
                priceManagementService.findPrices(vendorId)
        );
        model.addAttribute(
                "missingItems",
                priceManagementService.findMissingItems(vendorId)
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

        return "vendor-detail";
    }

    @PostMapping("/vendor-management/{vendorId}/statement-delivery")
    public String updateStatementDeliveryMethod(
            @PathVariable Long vendorId,
            @RequestParam StatementDeliveryMethod statementDeliveryMethod,
            @RequestParam String month,
            RedirectAttributes redirectAttributes
    ) {
        try {
            var vendor = vendorRepository.findById(vendorId)
                    .orElseThrow(() -> new IllegalArgumentException("거래처를 찾을 수 없습니다."));
            vendor.updateStatementDeliveryMethod(statementDeliveryMethod);
            vendorRepository.save(vendor);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "명세서 전달방식을 " + statementDeliveryMethod.getLabel() + "로 저장했습니다."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return redirectDetail(vendorId, month);
    }

    @PostMapping("/vendor-management/{vendorId}/prices/{priceId}")
    public String updateBasePrice(
            @PathVariable Long vendorId,
            @PathVariable Long priceId,
            @RequestParam BigDecimal unitPrice,
            @RequestParam String month,
            RedirectAttributes redirectAttributes
    ) {
        try {
            priceManagementService.updatePrice(priceId, unitPrice);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "거래처 기본단가를 수정했습니다."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }
        return redirectDetail(vendorId, month);
    }

    @PostMapping("/vendor-management/{vendorId}/prices")
    public String createBasePrice(
            @PathVariable Long vendorId,
            @RequestParam String itemName,
            @RequestParam BigDecimal unitPrice,
            @RequestParam String month,
            RedirectAttributes redirectAttributes
    ) {
        try {
            priceManagementService.createOrUpdatePrice(
                    vendorId,
                    itemName,
                    unitPrice
            );
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "거래처 기본단가를 추가했습니다."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }
        return redirectDetail(vendorId, month);
    }

    @PostMapping("/vendor-management/{vendorId}/items/{itemId}/price")
    public String updateOrderPrice(
            @PathVariable Long vendorId,
            @PathVariable Long itemId,
            @RequestParam BigDecimal quantity,
            @RequestParam(required = false) BigDecimal unitPrice,
            @RequestParam String month,
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
                    "해당 주문의 적용단가만 수정했습니다. 기본단가는 변경되지 않습니다."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }
        return redirectDetail(vendorId, month);
    }

    private YearMonth resolveMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            return YearMonth.now();
        }
    }

    private String redirectDetail(Long vendorId, String month) {
        return "redirect:/vendor-management/"
                + vendorId
                + "?month="
                + resolveMonth(month);
    }
}
