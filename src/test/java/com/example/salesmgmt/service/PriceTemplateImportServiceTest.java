package com.example.salesmgmt.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PriceTemplateImportServiceTest {

    private final PriceTemplateImportService service =
            new PriceTemplateImportService(new VendorRuleService());

    @Test
    void 일반_아포농협_팔공식품_단가를_추출한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createWorkbook()
        );

        var result = service.importTemplate(file);

        assertThat(result.errorCount()).isZero();
        assertThat(result.rows())
                .anyMatch(row ->
                        row.inputVendor().equals("옥계빅")
                                && row.itemName().equals("일반콩나물")
                                && row.unitPrice().intValueExact() == 11000
                )
                .anyMatch(row ->
                        row.inputVendor().equals("아포농협")
                                && row.itemName().equals("손두부")
                                && row.unitPrice().intValueExact() == 17000
                )
                .anyMatch(row ->
                        row.inputVendor().equals("팔공식품")
                                && row.itemName().equals("두부판")
                                && row.unitPrice().intValueExact() == 1000
                );
    }

    private byte[] createWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet general = workbook.createSheet("옥계빅");
            set(general, 12, 0, "일반콩나물");
            set(general, 12, 3, 11000);
            set(general, 22, 0, "회수통현황");
            set(general, 24, 3, 3000);

            Sheet apo = workbook.createSheet("아포농협");
            set(apo, 14, 0, "곱슬콩나물");
            set(apo, 14, 3, 12000);
            set(apo, 16, 0, "손두부");
            set(apo, 16, 3, 17000);

            Sheet palgong = workbook.createSheet("팔공식품");
            set(palgong, 10, 0, "두절kg");
            set(palgong, 10, 3, 1900);
            set(palgong, 10, 6, "손두부");
            set(palgong, 10, 9, 16000);
            set(palgong, 12, 6, "두부판");
            set(palgong, 12, 9, 1000);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void set(Sheet sheet, int rowIndex, int columnIndex, Object value) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }

        if (value instanceof Number number) {
            row.createCell(columnIndex).setCellValue(number.doubleValue());
        } else {
            row.createCell(columnIndex).setCellValue(value.toString());
        }
    }
}
