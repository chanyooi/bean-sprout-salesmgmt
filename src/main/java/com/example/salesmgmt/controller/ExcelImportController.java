package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.ExcelImportResult;
import com.example.salesmgmt.domain.SaveResult;
import com.example.salesmgmt.exception.SalesDataConflictException;
import com.example.salesmgmt.service.ExcelImportService;
import com.example.salesmgmt.service.SalesPersistenceService;
import com.example.salesmgmt.service.UploadHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ExcelImportController {

    private static final int PREVIEW_LIMIT = 300;

    private final ExcelImportService excelImportService;
    private final SalesPersistenceService salesPersistenceService;
    private final UploadHistoryService uploadHistoryService;

    public ExcelImportController(
            ExcelImportService excelImportService,
            SalesPersistenceService salesPersistenceService,
            UploadHistoryService uploadHistoryService
    ) {
        this.excelImportService = excelImportService;
        this.salesPersistenceService = salesPersistenceService;
        this.uploadHistoryService = uploadHistoryService;
    }

    @GetMapping("/upload")
    public String uploadPage() {
        return "upload";
    }

    @PostMapping("/excel/import")
    public String importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "preview") String action,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            ExcelImportResult result = excelImportService.importInputData(file);

            model.addAttribute("result", result);
            model.addAttribute(
                    "previewRecords",
                    result.records().stream().limit(PREVIEW_LIMIT).toList()
            );
            model.addAttribute("previewLimit", PREVIEW_LIMIT);

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
                    String beforeSnapshot =
                            uploadHistoryService.captureSalesSnapshot();

                    SaveResult saveResult = salesPersistenceService.save(
                            result.records(),
                            result.orderSnapshots()
                    );

                    uploadHistoryService.recordSuccess(
                            file.getOriginalFilename(),
                            beforeSnapshot,
                            saveResult
                    );

                    /*
                     * 저장 성공 뒤에는 POST 응답으로 화면을 직접 반환하지 않고
                     * GET /upload 로 이동한다. 새로고침/뒤로가기로 POST가 재전송되는
                     * 문제를 막는 PRG(Post-Redirect-Get) 패턴이다.
                     */
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

        /* 미리보기/검증 오류는 같은 화면에 결과를 보여줘야 하므로 그대로 렌더링 */
        return "upload";
    }
}
