package com.example.salesmgmt.service;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class InputTemplateWorkbookServiceTest {

    private final InputTemplateWorkbookService service =
            new InputTemplateWorkbookService();

    @Test
    void 구월_장부는_30개_시트이고_원본_주문번호_날짜수식을_보존한다()
            throws Exception {

        byte[] bytes = service.createBlankWorkbook(
                YearMonth.of(2026, 9)
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(bytes)
        )) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(30);
            assertThat(workbook.getSheetAt(0).getSheetName())
                    .isEqualTo("20260901");
            assertThat(workbook.getSheetAt(29).getSheetName())
                    .isEqualTo("20260930");

            var sheet = workbook.getSheetAt(0);
            var firstVendorRow = sheet.getRow(4);

            assertThat(firstVendorRow.getCell(0).getCellType())
                    .isEqualTo(CellType.FORMULA);
            assertThat(firstVendorRow.getCell(0).getCellFormula())
                    .contains("TEXT(B5");

            assertThat(firstVendorRow.getCell(1).getCellType())
                    .isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.getLocalDateTime(
                    firstVendorRow.getCell(1).getNumericCellValue()
            ).toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 1));

            assertThat(sheet.getRow(5).getCell(1).getCellType())
                    .isEqualTo(CellType.FORMULA);

            for (int column = 3; column <= 15; column++) {
                assertThat(firstVendorRow.getCell(column).getCellType())
                        .isEqualTo(CellType.BLANK);
            }
        }

        assertThat(hasZipEntry(bytes, "xl/calcChain.xml")).isFalse();
    }

    @Test
    void 윤년_이월은_29개_시트를_만든다() throws Exception {
        byte[] bytes = service.createBlankWorkbook(
                YearMonth.of(2028, 2)
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(bytes)
        )) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(29);
            assertThat(workbook.getSheetAt(28).getSheetName())
                    .isEqualTo("20280229");
        }
    }

    private boolean hasZipEntry(byte[] bytes, String target)
            throws Exception {
        try (ZipInputStream inputStream = new ZipInputStream(
                new ByteArrayInputStream(bytes)
        )) {
            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                if (target.equals(entry.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
