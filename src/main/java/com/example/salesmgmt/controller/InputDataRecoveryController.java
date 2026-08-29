package com.example.salesmgmt.controller;

import com.example.salesmgmt.service.InputDataRecoveryJobService;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Controller
public class InputDataRecoveryController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final InputDataRecoveryJobService recoveryJobService;

    public InputDataRecoveryController(
            InputDataRecoveryJobService recoveryJobService
    ) {
        this.recoveryJobService = recoveryJobService;
    }

    /**
     * 현재 DB 내용을 매일 업로드하던 input_data.xlsx 원본 양식으로 복구합니다.
     * 무거운 POI 작업은 공용 백그라운드 워커에서 실행해 요청 타임아웃을 피합니다.
     */
    @GetMapping("/input-data/recovery")
    public String startRecovery(
            @RequestParam String month,
            @RequestParam(required = false) String through
    ) {
        YearMonth selectedMonth = parseMonth(month);
        LocalDate endDate = parseEndDate(selectedMonth, through);

        String jobId = recoveryJobService.start(selectedMonth, endDate);
        return "redirect:/input-data/recovery/wait?job=" + jobId;
    }

    @GetMapping("/input-data/recovery/wait")
    public String waitForRecovery(
            @RequestParam String job,
            Model model
    ) {
        InputDataRecoveryJobService.JobSnapshot snapshot =
                recoveryJobService.status(job);

        if (snapshot.state() == InputDataRecoveryJobService.JobState.READY) {
            return "redirect:/input-data/recovery/result?job=" + job;
        }

        model.addAttribute("jobId", job);
        model.addAttribute("jobState", snapshot.state().name());
        model.addAttribute("jobError", snapshot.error());
        return "input-data-recovery-wait";
    }

    @GetMapping("/input-data/recovery/result")
    public ResponseEntity<?> downloadRecovery(@RequestParam String job) {
        Path path = recoveryJobService.resultPath(job);
        if (path == null) {
            return ResponseEntity.status(409)
                    .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                    .body("복구 파일이 아직 준비되지 않았거나 작업이 만료되었습니다.");
        }

        String filename = recoveryJobService.filename(job);
        if (filename == null || filename.isBlank()) {
            filename = "input_data_복구.xlsx";
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
                    .body("복구된 장부 파일을 읽지 못했습니다. 다시 시도해주세요.");
        }
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "정산월 형식이 올바르지 않습니다. 예: 2026-08"
            );
        }
    }

    private LocalDate parseEndDate(
            YearMonth month,
            String through
    ) {
        LocalDate endDate;
        try {
            endDate = (through == null || through.isBlank())
                    ? month.atEndOfMonth()
                    : LocalDate.parse(through);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("기준일 형식이 올바르지 않습니다.");
        }

        if (!YearMonth.from(endDate).equals(month)) {
            throw new IllegalArgumentException(
                    "기준일은 선택한 정산월 안의 날짜여야 합니다."
            );
        }
        return endDate;
    }
}
