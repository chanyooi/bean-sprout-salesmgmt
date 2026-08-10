package com.example.salesmgmt.controller;

import com.example.salesmgmt.service.InputTemplateWorkbookService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Controller
public class InputTemplateController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final InputTemplateWorkbookService workbookService;

    public InputTemplateController(
            InputTemplateWorkbookService workbookService
    ) {
        this.workbookService = workbookService;
    }

    @GetMapping("/input-template")
    public String page(
            @RequestParam(required = false) String month,
            Model model
    ) {
        YearMonth selectedMonth = parseMonthOrDefault(month);
        model.addAttribute("selectedMonth", selectedMonth.toString());
        model.addAttribute("dayCount", selectedMonth.lengthOfMonth());
        return "input-template";
    }

    @GetMapping("/input-template/download")
    public ResponseEntity<byte[]> download(
            @RequestParam String month
    ) {
        YearMonth selectedMonth = parseRequiredMonth(month);
        byte[] fileBytes = workbookService.createBlankWorkbook(selectedMonth);

        String filename = "input_data_"
                + selectedMonth.toString()
                + ".xlsx";

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString()
                )
                .contentLength(fileBytes.length)
                .body(fileBytes);
    }

    private YearMonth parseMonthOrDefault(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }

        return parseRequiredMonth(month);
    }

    private YearMonth parseRequiredMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "월 형식이 올바르지 않습니다. 예: 2026-08"
            );
        }
    }
}
