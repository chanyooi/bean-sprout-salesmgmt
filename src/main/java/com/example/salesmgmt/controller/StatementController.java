package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.StatementDeliveryMethod;
import com.example.salesmgmt.domain.StatementWorkbookResult;
import com.example.salesmgmt.service.FilteredStatementWorkbookService;
import com.example.salesmgmt.service.SalesManagementService;
import com.example.salesmgmt.service.SingleVendorStatementWorkbookService;
import com.example.salesmgmt.service.StatementGenerationJobService;
import com.example.salesmgmt.service.StatementTemplateStorageService;
import com.example.salesmgmt.service.StatementWorkbookOnePassService;
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
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/statements")
public class StatementController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            );

    private final StatementWorkbookOnePassService statementWorkbookService;
    private final FilteredStatementWorkbookService filteredStatementWorkbookService;
    private final SingleVendorStatementWorkbookService singleVendorStatementWorkbookService;
    private final SalesManagementService salesManagementService;
    private final StatementTemplateStorageService statementTemplateStorageService;
    private final StatementGenerationJobService statementGenerationJobService;

    public StatementController(
            StatementWorkbookOnePassService statementWorkbookService,
            FilteredStatementWorkbookService filteredStatementWorkbookService,
            SingleVendorStatementWorkbookService singleVendorStatementWorkbookService,
            SalesManagementService salesManagementService,
            StatementTemplateStorageService statementTemplateStorageService,
            StatementGenerationJobService statementGenerationJobService
    ) {
        this.statementWorkbookService = statementWorkbookService;
        this.filteredStatementWorkbookService = filteredStatementWorkbookService;
        this.singleVendorStatementWorkbookService = singleVendorStatementWorkbookService;
        this.salesManagementService = salesManagementService;
        this.statementTemplateStorageService = statementTemplateStorageService;
        this.statementGenerationJobService = statementGenerationJobService;
    }

    @GetMapping
    public String form(
            @RequestParam(required = false) String month,
            Model model
    ) {
        YearMonth selectedMonth = salesManagementService.resolveMonth(month);
        model.addAttribute("selectedMonth", selectedMonth.toString());
        model.addAttribute("currentTemplateFilename", statementTemplateStorageService.currentFilename());
        model.addAttribute("currentTemplateUpdatedAt", statementTemplateStorageService.currentUpdatedAt());
        return "statements";
    }

    @GetMapping("/vendor-download")
    public ResponseEntity<byte[]> downloadSingleVendor(
            @RequestParam Long vendorId,
            @RequestParam String month
    ) {
        try {
            StatementWorkbookResult result = singleVendorStatementWorkbookService.generate(
                    vendorId,
                    YearMonth.parse(month)
            );
            return fileResponse(result);
        } catch (DateTimeParseException exception) {
            return badRequest("생성 월 형식이 올바르지 않습니다.");
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }
    }

    @PostMapping("/download/start")
    public ResponseEntity<Map<String, String>> startDownload(
            @RequestParam(value = "templateFile", required = false) MultipartFile templateFile,
            @RequestParam String month,
            @RequestParam(defaultValue = "false") boolean includeEmpty,
            @RequestParam(required = false) StatementDeliveryMethod deliveryMethod
    ) {
        try {
            YearMonth selectedMonth = YearMonth.parse(month);
            MultipartFile effectiveTemplate =
                    statementTemplateStorageService.resolveAndSaveIfUploaded(templateFile);

            String jobId = statementGenerationJobService.start(
                    effectiveTemplate,
                    selectedMonth,
                    includeEmpty,
                    deliveryMethod
            );
            return ResponseEntity.accepted().body(Map.of("jobId", jobId));
        } catch (DateTimeParseException exception) {
            return jsonBadRequest("생성 월 형식이 올바르지 않습니다.");
        } catch (IllegalArgumentException exception) {
            return jsonBadRequest(exception.getMessage());
        }
    }

    @GetMapping("/download/status")
    public ResponseEntity<Map<String, String>> downloadStatus(
            @RequestParam String jobId
    ) {
        StatementGenerationJobService.JobSnapshot snapshot =
                statementGenerationJobService.status(jobId);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("state", snapshot.state().name());
        if (snapshot.filename() != null) {
            body.put("filename", snapshot.filename());
        }
        if (snapshot.error() != null) {
            body.put("error", snapshot.error());
        }

        if (snapshot.state() == StatementGenerationJobService.JobState.NOT_FOUND) {
            return ResponseEntity.status(404).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/download/result")
    public ResponseEntity<byte[]> downloadResult(
            @RequestParam String jobId
    ) {
        StatementWorkbookResult result = statementGenerationJobService.result(jobId);
        if (result == null) {
            return ResponseEntity.status(409)
                    .contentType(new MediaType(
                            "text",
                            "plain",
                            StandardCharsets.UTF_8
                    ))
                    .body("명세서가 아직 준비되지 않았습니다.".getBytes(StandardCharsets.UTF_8));
        }
        return fileResponse(result);
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam(value = "templateFile", required = false) MultipartFile templateFile,
            @RequestParam String month,
            @RequestParam(defaultValue = "false") boolean includeEmpty,
            @RequestParam(required = false) StatementDeliveryMethod deliveryMethod
    ) {
        try {
            YearMonth selectedMonth = YearMonth.parse(month);
            MultipartFile effectiveTemplate =
                    statementTemplateStorageService.resolveAndSaveIfUploaded(templateFile);

            StatementWorkbookResult result = deliveryMethod == null
                    ? statementWorkbookService.generate(
                            effectiveTemplate,
                            selectedMonth,
                            includeEmpty
                    )
                    : filteredStatementWorkbookService.generate(
                            effectiveTemplate,
                            selectedMonth,
                            includeEmpty,
                            deliveryMethod
                    );

            return fileResponse(result);
        } catch (DateTimeParseException exception) {
            return badRequest("생성 월 형식이 올바르지 않습니다.");
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }
    }

    private ResponseEntity<byte[]> fileResponse(StatementWorkbookResult result) {
        String encodedFilename = URLEncoder.encode(
                result.filename(),
                StandardCharsets.UTF_8
        ).replace("+", "%20");

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename
                )
                .header("X-Generated-Sheets", Integer.toString(result.generatedSheetCount()))
                .header("X-Sheets-With-Sales", Integer.toString(result.sheetWithSalesCount()))
                .header("X-Generation-Warnings", Integer.toString(result.warningCount()))
                .contentType(XLSX_MEDIA_TYPE)
                .body(result.fileBytes());
    }

    private ResponseEntity<byte[]> badRequest(String message) {
        String safeMessage = safeMessage(message);
        return ResponseEntity.badRequest()
                .contentType(new MediaType(
                        "text",
                        "plain",
                        StandardCharsets.UTF_8
                ))
                .body(safeMessage.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<Map<String, String>> jsonBadRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of(
                "error",
                safeMessage(message)
        ));
    }

    private String safeMessage(String message) {
        return message == null || message.isBlank()
                ? "명세서를 생성하지 못했습니다."
                : message;
    }
}
