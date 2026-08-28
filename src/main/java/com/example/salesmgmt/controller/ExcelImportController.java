package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.ExcelImportResult;
import com.example.salesmgmt.domain.SaveResult;
import com.example.salesmgmt.exception.SalesDataConflictException;
import com.example.salesmgmt.service.ExcelImportJobService;
import com.example.salesmgmt.service.ExcelImportService;
import com.example.salesmgmt.service.SalesPersistenceService;
import com.example.salesmgmt.service.UploadHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ExcelImportController {

    private static final int PREVIEW_LIMIT = 300;

    private final ExcelImportService excelImportService;
    private final SalesPersistenceService salesPersistenceService;
    private final UploadHistoryService uploadHistoryService;
    private final ExcelImportJobService excelImportJobService;

    public ExcelImportController(
            ExcelImportService excelImportService,
            SalesPersistenceService salesPersistenceService,
            UploadHistoryService uploadHistoryService,
            ExcelImportJobService excelImportJobService
    ) {
        this.excelImportService = excelImportService;
        this.salesPersistenceService = salesPersistenceService;
        this.uploadHistoryService = uploadHistoryService;
        this.excelImportJobService = excelImportJobService;
    }

    @GetMapping("/upload")
    public String uploadPage(
            @RequestParam(required = false) String job,
            Model model
    ) {
        if (job != null && !job.isBlank()) {
            var jobView = excelImportJobService.find(job);
            if (jobView.isPresent() && jobView.get().finished()) {
                return "redirect:/excel/import/jobs/" + job + "/result";
            }
            if (jobView.isPresent()) {
                model.addAttribute("jobId", job);
                model.addAttribute("jobFilename", jobView.get().originalFilename());
                return "upload-processing";
            }
            model.addAttribute("fatalError", "업로드 처리 정보를 찾을 수 없습니다. 파일을 다시 올려주세요.");
        }
        return "upload";
    }

    /**
     * Railway 프록시의 요청 시간 제한을 피하기 위한 기본 업로드 경로.
     * 파일 수신/임시 저장까지만 현재 요청에서 처리하고, 엑셀 검사와 DB 저장은
     * 백그라운드 작업으로 넘긴 뒤 곧바로 처리 상태 화면으로 이동한다.
     */
    @PostMapping({"/excel/import", "/excel/import/start"})
    public String startImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "preview") String action,
            RedirectAttributes redirectAttributes
    ) {
        try {
            String jobId = excelImportJobService.start(file, action);
            return "redirect:/upload?job=" + jobId;
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("fatalError", exception.getMessage());
            return "redirect:/upload";
        }
    }

    @GetMapping("/excel/import/jobs/{jobId}/status")
    @ResponseBody
    public ImportJobStatus importJobStatus(@PathVariable String jobId) {
        return excelImportJobService.find(jobId)
                .map(job -> new ImportJobStatus(
                        job.state().name(),
                        job.finished()
                                ? "처리가 끝났습니다. 결과 화면으로 이동합니다."
                                : "엑셀을 검사하고 판매자료를 동기화하는 중입니다.",
                        job.finished()
                                ? "/excel/import/jobs/" + jobId + "/result"
                                : null
                ))
                .orElseGet(() -> new ImportJobStatus(
                        "MISSING",
                        "업로드 처리 정보를 찾을 수 없습니다. 파일을 다시 올려주세요.",
                        "/upload"
                ));
    }

    @GetMapping("/excel/import/jobs/{jobId}/result")
    public String importJobResult(
            @PathVariable String jobId,
            Model model
    ) {
        var optionalJob = excelImportJobService.find(jobId);
        if (optionalJob.isEmpty()) {
            model.addAttribute("fatalError", "업로드 처리 결과가 만료되었습니다. 파일을 다시 올려주세요.");
            return "upload";
        }

        var job = optionalJob.get();
        if (!job.finished()) {
            return "redirect:/upload?job=" + jobId;
        }

        if (job.result() != null) {
            addPreviewModel(model, job.result());
        }
        if (job.saveResult() != null) {
            model.addAttribute("saveResult", job.saveResult());
            model.addAttribute("successMessage", "판매자료를 저장했습니다.");
        }
        if (job.fatalError() != null && !job.fatalError().isBlank()) {
            model.addAttribute("fatalError", job.fatalError());
        }
        if (job.saveError() != null && !job.saveError().isBlank()) {
            model.addAttribute("saveError", job.saveError());
        }
        return "upload";
    }

    /**
     * 문제 진단용 동기 경로. 일반 업로드 화면에서는 호출하지 않는다.
     */
    @PostMapping("/excel/import/sync")
    public String importExcelSynchronously(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "preview") String action,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            ExcelImportResult result = excelImportService.importInputData(file);
            addPreviewModel(model, result);

            if ("save".equals(action)) {
                if (result.errorCount() > 0) {
                    model.addAttribute(
                            "saveError",
                            "엑셀 오류가 있어 저장하지 않았습니다. 오류를 수정한 뒤 다시 올려주세요."
                    );
                } else if (result.orderSnapshots().isEmpty()) {
                    model.addAttribute(
                            "saveError",
                            "동기화할 거래처 행이 없습니다."
                    );
                } else {
                    String beforeSnapshot = uploadHistoryService.captureSalesSnapshot();
                    SaveResult saveResult = salesPersistenceService.save(
                            result.records(),
                            result.orderSnapshots()
                    );
                    uploadHistoryService.recordSuccess(
                            file.getOriginalFilename(),
                            beforeSnapshot,
                            saveResult
                    );
                    redirectAttributes.addFlashAttribute("saveResult", saveResult);
                    redirectAttributes.addFlashAttribute(
                            "successMessage",
                            "판매자료를 저장했습니다."
                    );
                    return "redirect:/upload";
                }
            }
        } catch (SalesDataConflictException exception) {
            model.addAttribute("saveError", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            model.addAttribute("fatalError", exception.getMessage());
        }

        return "upload";
    }

    private void addPreviewModel(Model model, ExcelImportResult result) {
        model.addAttribute("result", result);
        model.addAttribute(
                "previewRecords",
                result.records().stream().limit(PREVIEW_LIMIT).toList()
        );
        model.addAttribute("previewLimit", PREVIEW_LIMIT);
    }

    public record ImportJobStatus(
            String state,
            String message,
            String resultUrl
    ) {
    }
}
