package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.DailySalesCalendarView;
import com.example.salesmgmt.service.DailySalesCalendarService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DailySalesCalendarController {

    private final DailySalesCalendarService service;

    public DailySalesCalendarController(
            DailySalesCalendarService service
    ) {
        this.service = service;
    }

    @GetMapping("/sales-calendar")
    public String calendar(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String date,
            Model model
    ) {
        DailySalesCalendarView calendar =
                service.create(month, date);

        model.addAttribute(
                "calendar",
                calendar
        );

        return "sales-calendar";
    }
}
