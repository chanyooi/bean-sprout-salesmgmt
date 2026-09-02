package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.ItemCatalog;
import com.example.salesmgmt.domain.PaymentCycle;
import com.example.salesmgmt.domain.RouteCode;
import com.example.salesmgmt.domain.StatementDeliveryMethod;
import com.example.salesmgmt.repository.VendorRepository;
import com.example.salesmgmt.service.PriceManagementService;
import com.example.salesmgmt.service.SalesManagementService;
import com.example.salesmgmt.service.VendorDetailService;
import com.example.salesmgmt.service.VendorManagementService;
import com.example.salesmgmt.service.VendorRegistrationService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class VendorHubController {

    private final PriceManagementService priceManagementService;
    private final SalesManagementService salesManagementService;
    private final VendorManagementService vendorManagementService;
    private final VendorDetailService vendorDetailService;
    private final VendorRepository vendorRepository;
    private final VendorRegistrationService vendorRegistrationService;

    public VendorHubController(
            PriceManagementService priceManagementService,
            SalesManagementService salesManagementService,
            VendorManagementService vendorManagementService,
            VendorDetailService vendorDetailService,
            VendorRepository vendorRepository,
            VendorRegistrationService vendorRegistrationService
    ) {
        this.priceManagementService = priceManagementService;
        this.salesManagementService = salesManagementService;
        this.vendorManagementService = vendorManagementService;
        this.vendorDetailService = vendorDetailService;
        this.vendorRepository = vendorRepository;
        this.vendorRegistrationService = vendorRegistrationService;
    }

    @GetMapping("/vendor-management")
    public String vendorManagement(Model model) {
        model.addAttribute("vendors", priceManagementService.findVendors());
        model.addAttribute("vendorProfiles", vendorManagementService.findAllRows());
        model.addAttribute("routeCodes", RouteCode.values());
        model.addAttribute("paymentCycles", PaymentCycle.values());
        return "vendor-management";
    }

    @PostMapping("/vendor-management/profile/update")
    public String updateVendorProfile(
            @RequestParam Long vendorId,
            @RequestParam(defaultValue = "false") boolean active,
            @RequestParam RouteCode routeCode,
            @RequestParam(required = false) Integer routeOrder,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String phone,
            @RequestParam PaymentCycle paymentCycle,
            @RequestParam(required = false) String memo,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            RedirectAttributes redirectAttributes
    ) {
        try {
            vendorManagementService.updateProfile(
                    vendorId,
                    active,
                    routeCode,
                    routeOrder,
                    address,
                    phone,
                    paymentCycle,
                    memo,
                    latitude,
                    longitude
            );
            redirectAttributes.addFlashAttribute("successMessage", "거래처 기본정보를 저장했습니다.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/vendor-management";
    }

    @GetMapping("/vendor-management/new")
    public String newVendor(Model model) {
        model.addAttribute("items", ItemCatalog.ALL_ITEMS);
        model.addAttribute("paymentCycles", PaymentCycle.values());
        model.addAttribute("statementDeliveryMethods", StatementDeliveryMethod.values());
        return "vendor-create";
    }

    @PostMapping("/vendor-management/new")
    public String createVendor(
            @RequestParam String inputName,
            @RequestParam(required = false) String statementName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) PaymentCycle paymentCycle,
            @RequestParam(required = false) StatementDeliveryMethod statementDeliveryMethod,
            @RequestParam(required = false) List<String> itemName,
            @RequestParam(required = false) List<String> unitPrice,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Map<String, BigDecimal> prices = new LinkedHashMap<>();
            if (itemName != null) {
                for (int i = 0; i < itemName.size(); i++) {
                    String raw = unitPrice != null && i < unitPrice.size()
                            ? unitPrice.get(i)
                            : null;
                    if (raw == null || raw.isBlank()) continue;
                    prices.put(itemName.get(i), new BigDecimal(raw.trim()));
                }
            }

            Long vendorId = vendorRegistrationService.register(
                    inputName,
                    statementName,
                    phone,
                    address,
                    paymentCycle,
                    statementDeliveryMethod,
                    prices
            );

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "거래처를 추가했습니다. 장부에 같은 거래처명이 들어오면 자동 연결되고 명세서도 생성됩니다."
            );
            return "redirect:/vendor-management/" + vendorId;
        } catch (NumberFormatException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "단가는 숫자로 입력해주세요.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/vendor-management/new";
    }

    @GetMapping("/vendor-management/{vendorId}")
    public String vendorDetail(
            @PathVariable Long vendorId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year,
            Model model
    ) {
        YearMonth selectedMonth = resolveMonthAndYear(month, year);
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
        model.addAttribute("prices", priceManagementService.findPrices(vendorId));
        model.addAttribute("missingItems", priceManagementService.findMissingItems(vendorId));
        model.addAttribute("selectedMonth", selectedMonth.toString());
        model.addAttribute("selectedYear", selectedMonth.getYear());
        model.addAttribute("previousYear", selectedMonth.getYear() - 1);
        model.addAttribute("nextYear", selectedMonth.getYear() + 1);
        model.addAttribute("previousMonth", selectedMonth.minusMonths(1).toString());
        model.addAttribute("nextMonth", selectedMonth.plusMonths(1).toString());
        model.addAttribute(
                "historicalDefaultMonth",
                selectedMonth.isBefore(VendorDetailService.SYSTEM_START_MONTH)
                        ? selectedMonth.toString()
                        : VendorDetailService.SYSTEM_START_MONTH.minusMonths(1).toString()
        );

        return "vendor-detail";
    }

    @PostMapping("/vendor-management/{vendorId}/historical-spend")
    public String saveHistoricalSpend(
            @PathVariable Long vendorId,
            @RequestParam String historicalMonth,
            @RequestParam BigDecimal amount,
            @RequestParam String month,
            RedirectAttributes redirectAttributes
    ) {
        try {
            YearMonth targetMonth = YearMonth.parse(historicalMonth);
            vendorDetailService.saveHistoricalMonthlySpend(vendorId, targetMonth, amount);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    targetMonth + " 과거 사용금액을 저장했습니다."
            );
            return "redirect:/vendor-management/" + vendorId + "?month=" + targetMonth;
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage() == null
                            ? "과거 사용금액을 저장하지 못했습니다."
                            : exception.getMessage()
            );
            return redirectDetail(vendorId, month);
        }
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
            YearMonth selectedMonth = resolveMonth(month);
            priceManagementService.updatePriceForMonth(
                    priceId,
                    unitPrice,
                    selectedMonth
            );
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "기본단가를 수정했습니다. 이 달의 일반단가 주문만 새 단가로 바뀌고, 특정 날짜에 직접 수정한 행사단가는 그대로 유지됩니다."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
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
            priceManagementService.createOrUpdatePriceForMonth(
                    vendorId,
                    itemName,
                    unitPrice,
                    resolveMonth(month)
            );
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "거래처 기본단가를 추가했습니다."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
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
            salesManagementService.updateItem(itemId, quantity, unitPrice);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "해당 날짜 주문의 적용단가만 수정했습니다. 엑셀 명세서도 이 금액으로 계산됩니다."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return redirectDetail(vendorId, month);
    }

    private YearMonth resolveMonthAndYear(String month, Integer year) {
        YearMonth resolved = resolveMonth(month);
        if (year == null) return resolved;
        int safeYear = Math.max(2000, Math.min(2100, year));
        return YearMonth.of(safeYear, resolved.getMonthValue());
    }

    private YearMonth resolveMonth(String month) {
        if (month == null || month.isBlank()) return YearMonth.now();
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            return YearMonth.now();
        }
    }

    private String redirectDetail(Long vendorId, String month) {
        return "redirect:/vendor-management/" + vendorId + "?month=" + resolveMonth(month);
    }
}
