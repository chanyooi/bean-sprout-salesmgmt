package com.example.salesmgmt.controller;

import com.example.salesmgmt.service.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Controller
public class AdminSafetyController {

    private final UploadHistoryService uploadHistoryService;
    private final DatabaseBackupService databaseBackupService;
    private final MonthlyCloseService monthlyCloseService;

    public AdminSafetyController(
            UploadHistoryService uploadHistoryService,
            DatabaseBackupService databaseBackupService,
            MonthlyCloseService monthlyCloseService
    ) {
        this.uploadHistoryService = uploadHistoryService;
        this.databaseBackupService = databaseBackupService;
        this.monthlyCloseService = monthlyCloseService;
    }

    @GetMapping("/admin/safety")
    public String page(Model model) {
        model.addAttribute(
                "histories",
                uploadHistoryService.findRecent()
        );
        model.addAttribute(
                "latestRestorableId",
                uploadHistoryService.latestRestorableId()
        );
        model.addAttribute(
                "closures",
                monthlyCloseService.findClosures()
        );
        model.addAttribute(
                "currentMonth",
                YearMonth.now().toString()
        );
        return "admin-safety";
    }

    @PostMapping("/admin/uploads/{id}/restore")
    public String restore(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            uploadHistoryService.restoreLatest(id);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "가장 최근 장부 업로드 직전 상태로 판매 데이터를 복구했습니다."
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/admin/safety";
    }

    @PostMapping("/admin/month-close")
    public String close(
            @RequestParam String month,
            RedirectAttributes redirectAttributes
    ) {
        try {
            YearMonth parsed = YearMonth.parse(month);
            monthlyCloseService.close(parsed);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    parsed + " 마감을 완료했습니다."
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }
        return "redirect:/admin/safety";
    }

    @PostMapping("/admin/month-reopen")
    public String reopen(
            @RequestParam String month,
            RedirectAttributes redirectAttributes
    ) {
        try {
            YearMonth parsed = YearMonth.parse(month);
            monthlyCloseService.reopen(parsed);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    parsed + " 마감을 해제했습니다."
            );
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }
        return "redirect:/admin/safety";
    }

    @GetMapping("/admin/backup/download")
    public ResponseEntity<byte[]> downloadBackup() {
        byte[] data = databaseBackupService.createBackupZip();

        String filename =
                "bean_sprout_backup_"
                        + java.time.LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern(
                                "yyyyMMdd_HHmmss"
                        ))
                        + ".zip";

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }
}
