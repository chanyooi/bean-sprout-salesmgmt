package com.example.salesmgmt.controller;

import com.example.salesmgmt.service.InputDataRecoveryJobService;
import com.example.salesmgmt.service.InputWorkbookSnapshotService;
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

    private final InputWorkbookSnapshotService snapshotService;
    private final InputDataRecoveryJobService recoveryJobService;

    public InputDataRecoveryController(
            InputWorkbookSnapshotService snapshotService,
            InputDataRecoveryJobService recoveryJobService
    ) {
        this.snapshotService = snapshotService;
        this.recoveryJobService = recoveryJobService;
    }

    /**
     * 평소 DB 반영에 사용했던 input_data.xlsx 원본 자체를 내려받습니다.
     * Apache POI로 새 파일을 만들지 않기 때문에 별도 복구 작업 대기 시간이 없습니다.
     * through 파라미터는 예전 화면과의 호환을 위해 받아도 무시합니다.
     */
    @GetMapping("/input-data/recovery")
    public ResponseEntity<?> downloadLatestUploadedWorkbook(
            @RequestParam String month,
            @RequestParam(required = false) String through
    ) {
        YearMonth selectedMonth = parseMonth(month);

        var stored = snapshotService.findLatest(selectedMonth);
        if (stored.isEmpty()) {
            return ResponseEntity.status(404)
                    .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                    .body(
                            selectedMonth
                                    + "에 저장된 업로드 원본이 아직 없습니다. "
                                    + "이 기능 적용 후 input_data.xlsx를 한 번 정상 업로드하면 "
                                    + "그 다음부터는 업로드했던 파일 그대로 즉시 다운로드할 수 있습니다."
                    );
        }

        InputWorkbookSnapshotService.StoredWorkbook workbook = stored.get();
        String filename = workbook.filename();
        if (filename == null || filename.isBlank()) {
            filename = "input_data.xlsx";
        }

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(workbook.bytes().length)
                .body(workbook.bytes());
    }

    /**
     * 예전 DB 재생성 방식의 대기/결과 URL은 기존 열린 탭 호환용으로 남겨둡니다.
     */
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
}
