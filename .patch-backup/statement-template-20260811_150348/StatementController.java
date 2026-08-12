package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.StatementWorkbookResult;
import com.example.salesmgmt.service.SalesManagementService;
import com.example.salesmgmt.service.StatementWorkbookService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Controller
@RequestMapping("/statements")
public class StatementController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            );

    private final StatementWorkbookService statementWorkbookService;
    private final SalesManagementService salesManagementService;

    public StatementController(
            StatementWorkbookService statementWorkbookService,
            SalesManagementService salesManagementService
    ) {
        this.statementWorkbookService = statementWorkbookService;
        this.salesManagementService = salesManagementService;
    }

    @GetMapping
    public String form(
            @RequestParam(required = false) String month,
            Model model
    ) {
        YearMonth selectedMonth =
                salesManagementService.resolveMonth(month);

        model.addAttribute("selectedMonth", selectedMonth.toString());
        return "statements";
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam("templateFile") MultipartFile templateFile,
            @RequestParam String month,
            @RequestParam(defaultValue = "false") boolean includeEmpty
    ) {
        try {
            YearMonth selectedMonth = YearMonth.parse(month);

            StatementWorkbookResult result =
                    statementWorkbookService.generate(
                            templateFile,
                            selectedMonth,
                            includeEmpty
                    );

            String encodedFilename = URLEncoder.encode(
                    result.filename(),
                    StandardCharsets.UTF_8
            ).replace("+", "%20");

            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''"
                                    + encodedFilename
                    )
                    .header(
                            "X-Generated-Sheets",
                            Integer.toString(result.generatedSheetCount())
                    )
                    .header(
                            "X-Sheets-With-Sales",
                            Integer.toString(result.sheetWithSalesCount())
                    )
                    .header(
                            "X-Generation-Warnings",
                            Integer.toString(result.warningCount())
                    )
                    .contentType(XLSX_MEDIA_TYPE)
                    .body(result.fileBytes());
        } catch (DateTimeParseException exception) {
            return badRequest("생성 월 형식이 올바르지 않습니다.");
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }
    }

    private ResponseEntity<byte[]> badRequest(String message) {
        String safeMessage = message == null || message.isBlank()
                ? "명세서를 생성하지 못했습니다."
                : message;

        return ResponseEntity.badRequest()
                .contentType(new MediaType(
                        "text",
                        "plain",
                        StandardCharsets.UTF_8
                ))
                .body(safeMessage.getBytes(StandardCharsets.UTF_8));
    }
}
