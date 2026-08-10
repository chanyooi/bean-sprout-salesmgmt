package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.ExcelImportResult;
import com.example.salesmgmt.domain.ImportIssue;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelImportServiceTest {

    private final ExcelImportService service =
            new ExcelImportService(new VendorRuleService());

    @Test
    void 가로형_입력값을_세로형_판매기록으로_변환한다() throws Exception {
        byte[] excel = createWorkbook("명희네해장");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "input_data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excel
        );

        ExcelImportResult result = service.importInputData(file);

        assertThat(result.sheetCount()).isEqualTo(1);
        assertThat(result.salesRowCount()).isEqualTo(1);
        assertThat(result.records()).hasSize(2);
        assertThat(result.orderSnapshots()).hasSize(1);

        assertThat(result.records().getFirst().inputVendor())
                .isEqualTo("명희네해장");
        assertThat(result.records().getFirst().statementVendor())
                .isEqualTo("명희네");

        assertThat(result.records())
                .extracting(record -> record.item())
                .containsExactly("일반콩나물", "회수통");
    }

    @Test
    void 산동빅은_데이터를_가져오되_템플릿_경고를_표시한다() throws Exception {
        byte[] excel = createWorkbook("산동빅");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "input_data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excel
        );

        ExcelImportResult result = service.importInputData(file);

        assertThat(result.records()).hasSize(2);
        assertThat(result.issues())
                .anyMatch(issue ->
                        issue.level() == ImportIssue.IssueLevel.WARNING
                                && issue.message().contains("명세서 템플릿이 없습니다")
                );
    }

    @Test
    void 품목이_전부_빈_거래처행도_주문스냅샷으로_가져온다() throws Exception {
        byte[] excel = createBlankWorkbook("HS식자재도매유통");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "input_data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excel
        );

        ExcelImportResult result = service.importInputData(file);

        assertThat(result.records()).isEmpty();
        assertThat(result.salesRowCount()).isZero();
        assertThat(result.orderSnapshots()).hasSize(1);
        assertThat(result.orderSnapshots().getFirst().orderNumber())
                .isEqualTo("20260705-007");
        assertThat(result.orderSnapshots().getFirst().inputVendor())
                .isEqualTo("HS식자재도매유통");
    }

    private byte[] createBlankWorkbook(String vendor) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("20260705");

            Row header = sheet.createRow(2);
            String[] headers = {
                    "주문번호",
                    "날짜",
                    "거래처",
                    "두절kg",
                    "일반콩나물",
                    "소립",
                    "곱슬콩나물",
                    "3.5kg일반",
                    "3.5kg곱슬",
                    "숙주",
                    "회수통",
                    "손두부",
                    "두부판",
                    "회수통단가",
                    "전달방식",
                    "비고"
            };

            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
            }

            sheet.createRow(3);

            // ROW()-4 = 7이 되도록 11행(0-based 10)에 HS 행을 둡니다.
            Row data = sheet.createRow(10);
            data.createCell(0).setCellValue("20260705-007");
            data.createCell(1).setCellValue("2026-07-05");
            data.createCell(2).setCellValue(vendor);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createWorkbook(String vendor) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("20260701");

            Row header = sheet.createRow(2);
            String[] headers = {
                    "주문번호",
                    "날짜",
                    "거래처",
                    "두절kg",
                    "일반콩나물",
                    "소립",
                    "곱슬콩나물",
                    "3.5kg일반",
                    "3.5kg곱슬",
                    "숙주",
                    "회수통",
                    "손두부",
                    "두부판",
                    "회수통단가",
                    "전달방식",
                    "비고"
            };

            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
            }

            sheet.createRow(3);

            Row data = sheet.createRow(4);
            data.createCell(0).setCellValue("20260701-001");
            data.createCell(1).setCellValue("2026-07-01");
            data.createCell(2).setCellValue(vendor);
            data.createCell(4).setCellValue(10);
            data.createCell(10).setCellValue(2);
            data.createCell(13).setCellValue(3500);
            data.createCell(14).setCellValue("카톡");
            data.createCell(15).setCellValue("테스트");

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
