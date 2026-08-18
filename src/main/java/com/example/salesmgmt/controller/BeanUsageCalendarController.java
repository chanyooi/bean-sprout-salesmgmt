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
        model.addAttribute("calendar", calendarService.load(selectedMonth));
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("previousMonth", selectedMonth.minusMonths(1));
        model.addAttribute("nextMonth", selectedMonth.plusMonths(1));
        model.addAttribute("origins", BeanOrigin.values());
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
            @RequestParam(defaultValue = "CANADA") BeanOrigin largeOrigin,
            @RequestParam(defaultValue = "CANADA") BeanOrigin mediumOrigin,
            @RequestParam(defaultValue = "CANADA") BeanOrigin smallOrigin,
            RedirectAttributes redirectAttributes
    ) {
        try {
            int saved = 0;
            saved += addIfPositive(usageDate, BeanType.LARGE, largeOrigin, largeBags);
            saved += addIfPositive(usageDate, BeanType.MEDIUM, mediumOrigin, mediumBags);
            saved += addIfPositive(usageDate, BeanType.SMALL, smallOrigin, smallBags);

            if (saved == 0) {
                throw new IllegalArgumentException("대립·중립·소립 중 하나 이상 수량을 입력해주세요.");
            }

            redirectAttributes.addFlashAttribute(
                    "inventoryMessage",
                    usageDate + " 콩 사용량을 추가했습니다."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "inventoryError",
                    exception.getMessage()
            );
        }

        return "redirect:/bean-usage?month=" + YearMonth.from(usageDate);
    }

    private int addIfPositive(
            LocalDate date,
            BeanType type,
            BeanOrigin origin,
            BigDecimal bags
    ) {
        if (bags == null || bags.signum() == 0) {
            return 0;
        }
        if (bags.signum() < 0) {
            throw new IllegalArgumentException("사용 수량은 0 이상이어야 합니다.");
        }
        beanInventoryService.addUsage(date, type, origin, bags, null);
        return 1;
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
