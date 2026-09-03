package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.StatementDeliveryMethod;
import com.example.salesmgmt.domain.StatementWorkbookResult;
import com.example.salesmgmt.service.FilteredStatementWorkbookService;
import com.example.salesmgmt.service.SalesManagementService;
import com.example.salesmgmt.service.SingleVendorStatementWorkbookService;
import com.example.salesmgmt.service.StatementFinalBillingPatchService;
import com.example.salesmgmt.service.StatementGenerationJobService;
import com.example.salesmgmt.service.StatementTemplateStorageService;
import com.example.salesmgmt.service.StatementWorkbookOnePassService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    private final StatementFinalBillingPatchService finalBillingPatchService;
    private final SalesManagementService salesManagementService;
    private final StatementTemplateStorageService statementTemplateStorageService;
    private final StatementGenerationJobService statementGenerationJobService;

    public StatementController(
            StatementWorkbookOnePassService statementWorkbookService,
            FilteredStatementWorkbookService filteredStatementWorkbookService,
            SingleVendorStatementWorkbookService singleVendorStatementWorkbookService,
            StatementFinalBillingPatchService finalBillingPatchService,
            SalesManagementService salesManagementService,
            StatementTemplateStorageService statementTemplateStorageService,
            StatementGenerationJobService statementGenerationJobService
    ) {
        this.statementWorkbookService = statementWorkbookService;
        this.filteredStatementWorkbookService = filteredStatementWorkbookService;
        this.singleVendorStatementWorkbookService = singleVendorStatementWorkbookService;
        this.finalBillingPatchService = finalBillingPatchService;
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

    @PostMapping("/template")
    public String saveTemplate(
            @RequestParam("templateFile") MultipartFile templateFile,
            @RequestParam(required = false) String month,
            RedirectAttributes redirectAttributes
    ) {
        YearMonth selectedMonth = salesManagementService.resolveMonth(month);
        try {
            if (templateFile == null || templateFile.isEmpty()) {
                throw new IllegalArgumentException("등록할 .xlsx 템플릿 파일을 선택해주세요.");
            }
            statementTemplateStorageService.resolveAndSaveIfUploaded(templateFile);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "새 기본 명세서 템플릿을 저장했습니다."
            );
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/statements?month=" + selectedMonth;
    }

    @GetMapping("/vendor-download")
    public ResponseEntity<byte[]> downloadSingleVendor(
            @RequestParam Long vendorId,
            @RequestParam String month
    ) {
        try {
            YearMonth selectedMonth = YearMonth.parse(month);
            StatementWorkbookResult result = singleVendorStatementWorkbookService.generate(
                    vendorId,
                    selectedMonth
            );
            result = finalBillingPatchService.patchVendor(
                    result,
                    vendorId,
                    selectedMonth
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
            StatementGenerationJobService.JobSnapshot snapshot =
                    statementGenerationJobService.status(jobId);

            Map<String, String> body = new LinkedHashMap<>();
            body.put("jobId", jobId);
            body.put("state", snapshot.state().name());
            if (snapshot.filename() != null) {
                body.put("filename", snapshot.filename());
            }
            if (snapshot.error() != null) {
                body.put("error", snapshot.error());
            }
            return ResponseEntity.accepted().body(body);
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
    public ResponseEntity<?> downloadResult(
            @RequestParam String jobId
    ) {
        StatementGenerationJobService.JobFileResult result =
                statementGenerationJobService.result(jobId);
        if (result == null) {
            return ResponseEntity.status(409)
                    .contentType(new MediaType(
                            "text",
                            "plain",
                            StandardCharsets.UTF_8
                    ))
                    .body("명세서가 아직 준비되지 않았습니다.");
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

            result = finalBillingPatchService.patchMonthly(
                    result,
                    selectedMonth
            );
            return fileResponse(result);
        } catch (DateTimeParseException exception) {
            return badRequest("생성 월 형식이 올바르지 않습니다.");
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }
    }

    private ResponseEntity<byte[]> fileResponse(StatementWorkbookResult result) {
        String encodedFilename = encodeFilename(result.filename());

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

    private ResponseEntity<?> fileResponse(
            StatementGenerationJobService.JobFileResult result
    ) {
        String encodedFilename = encodeFilename(result.filename());
        Resource resource = new FileSystemResource(result.path());

        try {
            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFilename
                    )
                    .header("X-Generated-Sheets", Integer.toString(result.generatedSheetCount()))
                    .header("X-Sheets-With-Sales", Integer.toString(result.sheetWithSalesCount()))
                    .header("X-Generation-Warnings", Integer.toString(result.warningCount()))
                    .contentType(XLSX_MEDIA_TYPE)
                    .contentLength(Files.size(result.path()))
                    .body(resource);
        } catch (IOException exception) {
            return ResponseEntity.internalServerError()
                    .contentType(new MediaType(
                            "text",
                            "plain",
                            StandardCharsets.UTF_8
                    ))
                    .body("생성된 명세서 파일을 읽지 못했습니다. 다시 생성해주세요.");
        }
    }

    private String encodeFilename(String filename) {
        return URLEncoder.encode(
                filename,
                StandardCharsets.UTF_8
        ).replace("+", "%20");
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
