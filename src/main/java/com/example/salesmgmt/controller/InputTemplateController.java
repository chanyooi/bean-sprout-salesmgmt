package com.example.salesmgmt.controller;

import com.example.salesmgmt.service.InputTemplateGenerationJobService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Controller
public class InputTemplateController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final InputTemplateGenerationJobService generationJobService;

    public InputTemplateController(
            InputTemplateGenerationJobService generationJobService
    ) {
        this.generationJobService = generationJobService;
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

    /**
     * 파일 생성이 끝날 때까지 Railway 요청을 붙잡지 않고 즉시 대기 화면으로 이동합니다.
     */
    @GetMapping("/input-template/download")
    public String startDownload(@RequestParam String month) {
        YearMonth selectedMonth = parseRequiredMonth(month);
        String jobId = generationJobService.start(selectedMonth);
        return "redirect:/input-template/download/wait?job=" + jobId;
    }

    @GetMapping("/input-template/download/wait")
    public String waitForDownload(
            @RequestParam String job,
            Model model
    ) {
        InputTemplateGenerationJobService.JobSnapshot snapshot =
                generationJobService.status(job);

        if (snapshot.state() == InputTemplateGenerationJobService.JobState.READY) {
            return "redirect:/input-template/download/result?job=" + job;
        }

        model.addAttribute("jobId", job);
        model.addAttribute("jobState", snapshot.state().name());
        model.addAttribute("jobError", snapshot.error());
        return "input-template-download-wait";
    }

    @GetMapping("/input-template/download/result")
    public ResponseEntity<?> downloadResult(@RequestParam String job) {
        Path path = generationJobService.resultPath(job);
        if (path == null) {
            return ResponseEntity.status(409)
                    .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                    .body("파일이 아직 준비되지 않았거나 작업이 만료되었습니다.");
        }

        String filename = generationJobService.filename(job);
        if (filename == null || filename.isBlank()) {
            filename = "input_data.xlsx";
        }

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        Resource resource = new FileSystemResource(path);
        try {
            return ResponseEntity.ok()
                    .contentType(XLSX_MEDIA_TYPE)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentLength(Files.size(path))
                    .body(resource);
        } catch (IOException exception) {
            return ResponseEntity.internalServerError()
                    .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                    .body("생성된 파일을 읽지 못했습니다. 다시 시도해주세요.");
        }
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
