package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.BeanOrigin;
import com.example.salesmgmt.domain.BeanType;
import com.example.salesmgmt.service.BeanInventoryService;
import com.example.salesmgmt.service.BeanUsageCalendarService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

@Controller
public class BeanUsageCalendarController {

    private final BeanUsageCalendarService calendarService;
    private final BeanInventoryService beanInventoryService;

    public BeanUsageCalendarController(
            BeanUsageCalendarService calendarService,
            BeanInventoryService beanInventoryService
    ) {
        this.calendarService = calendarService;
        this.beanInventoryService = beanInventoryService;
    }

    @GetMapping("/bean-usage")
    public String calendar(
            @RequestParam(required = false) String month,
            Model model
    ) {
        YearMonth selectedMonth = parseMonth(month);
        Map<String, BigDecimal> prices = beanInventoryService.getLatestUsagePricesPerKg();

        model.addAttribute("calendar", calendarService.load(selectedMonth));
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("previousMonth", selectedMonth.minusMonths(1));
        model.addAttribute("nextMonth", selectedMonth.plusMonths(1));
        model.addAttribute("origins", BeanOrigin.values());

        model.addAttribute("largeChinaUnitPrice", prices.get("LARGE_CHINA"));
        model.addAttribute("largeCanadaUnitPrice", prices.get("LARGE_CANADA"));
        model.addAttribute("mediumChinaUnitPrice", prices.get("MEDIUM_CHINA"));
        model.addAttribute("mediumCanadaUnitPrice", prices.get("MEDIUM_CANADA"));
        model.addAttribute("smallChinaUnitPrice", prices.get("SMALL_CHINA"));
        model.addAttribute("smallCanadaUnitPrice", prices.get("SMALL_CANADA"));

        return "bean-usage-calendar";
    }

    @PostMapping("/bean-usage/add")
    public String addDailyUsage(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate usageDate,
            @RequestParam(defaultValue = "0") BigDecimal largeBags,
            @RequestParam(defaultValue = "0") BigDecimal mediumBags,
            @RequestParam(defaultValue = "0") BigDecimal smallBags,
            @RequestParam(defaultValue = "CHINA") BeanOrigin largeOrigin,
            @RequestParam(defaultValue = "CHINA") BeanOrigin mediumOrigin,
            @RequestParam(defaultValue = "CHINA") BeanOrigin smallOrigin,
            @RequestParam(required = false) BigDecimal largeUnitPricePerKg,
            @RequestParam(required = false) BigDecimal mediumUnitPricePerKg,
            @RequestParam(required = false) BigDecimal smallUnitPricePerKg,
            RedirectAttributes redirectAttributes
    ) {
        try {
            validateRow("대립", largeBags, largeUnitPricePerKg);
            validateRow("중립", mediumBags, mediumUnitPricePerKg);
            validateRow("소립", smallBags, smallUnitPricePerKg);

            int saved = 0;
            saved += addIfPositive(
                    usageDate,
                    BeanType.LARGE,
                    largeOrigin,
                    largeBags,
                    largeUnitPricePerKg
            );
            saved += addIfPositive(
                    usageDate,
                    BeanType.MEDIUM,
                    mediumOrigin,
                    mediumBags,
                    mediumUnitPricePerKg
            );
            saved += addIfPositive(
                    usageDate,
                    BeanType.SMALL,
                    smallOrigin,
                    smallBags,
                    smallUnitPricePerKg
            );

            if (saved == 0) {
                throw new IllegalArgumentException("대립·중립·소립 중 하나 이상 수량을 입력해주세요.");
            }

            redirectAttributes.addFlashAttribute(
                    "inventoryMessage",
                    usageDate + " 콩 사용량과 kg당 단가를 추가했습니다."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "inventoryError",
                    exception.getMessage()
            );
        }

        return "redirect:/bean-usage?month=" + YearMonth.from(usageDate);
    }

    @PostMapping("/bean-usage/delete-day")
    public String deleteDailyUsage(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate usageDate,
            RedirectAttributes redirectAttributes
    ) {
        int deleted = calendarService.deleteDailyUsage(usageDate);

        if (deleted > 0) {
            redirectAttributes.addFlashAttribute(
                    "inventoryMessage",
                    usageDate + " 콩 사용 기록을 삭제했습니다."
            );
        } else {
            redirectAttributes.addFlashAttribute(
                    "inventoryError",
                    "삭제할 콩 사용 기록이 없습니다."
            );
        }

        return "redirect:/bean-usage?month=" + YearMonth.from(usageDate);
    }

    private int addIfPositive(
            LocalDate date,
            BeanType type,
            BeanOrigin origin,
            BigDecimal bags,
            BigDecimal unitPricePerKg
    ) {
        if (bags == null || bags.signum() == 0) {
            return 0;
        }

        beanInventoryService.addUsage(
                date,
                type,
                origin,
                bags,
                unitPricePerKg,
                null
        );
        return 1;
    }

    private void validateRow(
            String label,
            BigDecimal bags,
            BigDecimal unitPricePerKg
    ) {
        if (bags == null || bags.signum() == 0) {
            return;
        }
        if (bags.signum() < 0) {
            throw new IllegalArgumentException("사용 수량은 0 이상이어야 합니다.");
        }
        if (unitPricePerKg == null || unitPricePerKg.signum() <= 0) {
            throw new IllegalArgumentException(label + " kg당 단가를 입력해주세요.");
        }
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
