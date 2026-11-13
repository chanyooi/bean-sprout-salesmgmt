package com.example.salesmgmt.service;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementFinalBillingPatchServiceTest {

    @Test
    void templateContainsDetectableFinalBillingCell() throws Exception {
        ClassPathResource resource = new ClassPathResource("template.xlsx");

        int detected = 0;
        try (
                InputStream input = resource.getInputStream();
                XSSFWorkbook workbook = new XSSFWorkbook(input)
        ) {
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                if (StatementFinalBillingPatchService.writeFinalBillingAmount(
                        workbook.getSheetAt(index),
                        new BigDecimal("123456")
                )) {
                    detected++;
                }
            }
        }

        assertTrue(
                detected > 0,
                "template.xlsx에서 '최종 청구금액' 셀을 찾지 못했습니다."
        );
    }
    @Test
    void patchesMonthlyTotalAndFinalBillingValueBelowMergedLabel() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("VIP마트");

            var monthlyRow = sheet.createRow(4);
            monthlyRow.createCell(0).setCellValue("회수통 금액을 차감한\n이달 합계금");
            monthlyRow.createCell(4).setCellValue(1_997_000);
            sheet.addMergedRegion(new CellRangeAddress(4, 4, 0, 3));

            var finalLabelRow = sheet.createRow(6);
            finalLabelRow.createCell(4).setCellValue("최종 청구금액");
            sheet.addMergedRegion(new CellRangeAddress(6, 6, 4, 7));

            var finalAmountRow = sheet.createRow(7);
            finalAmountRow.createCell(4).setCellFormula("SUM(A1:A4)");
            sheet.addMergedRegion(new CellRangeAddress(7, 7, 4, 7));

            assertTrue(StatementFinalBillingPatchService.writeFinalBillingAmount(
                    sheet, new BigDecimal("1851000")
            ));

            assertEquals(1_851_000d, monthlyRow.getCell(4).getNumericCellValue());
            assertEquals(CellType.NUMERIC, finalAmountRow.getCell(4).getCellType());
            assertEquals(1_851_000d, finalAmountRow.getCell(4).getNumericCellValue());
            assertNull(finalLabelRow.getCell(8),
                    "청구금액은 제목 옆에 새로 쓰지 않고 실제 표시 셀을 고쳐야 합니다.");
        }
    }

    @Test
    void stillSupportsFinalBillingValueOnSameRow() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("기존양식");
            var row = sheet.createRow(2);
            row.createCell(0).setCellValue("최종청구금액");
            row.createCell(3).setCellValue(1_997_000);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 2));

            assertTrue(StatementFinalBillingPatchService.writeFinalBillingAmount(
                    sheet, new BigDecimal("1851000")
            ));
            assertEquals(1_851_000d, row.getCell(3).getNumericCellValue());
        }
    }

    @Test
    void updatesProductTotalsUsingActualOrderAmountInsteadOfTemplateUnitPrice() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("VIP마트");

            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("콩나물품목");
            header.createCell(1).setCellValue("수량");
            header.createCell(2).setCellValue("단가");
            header.createCell(3).setCellValue("금액");

            var general = sheet.createRow(1);
            general.createCell(0).setCellValue("일반콩나물");
            general.createCell(1).setCellValue(39);
            general.createCell(2).setCellValue(13000);
            general.createCell(3).setCellFormula("B2*C2");

            var returnLabel = sheet.createRow(2);
            returnLabel.createCell(0).setCellValue("회수통 현황");
            var returnRow = sheet.createRow(3);
            returnRow.createCell(1).setCellValue(65);
            returnRow.createCell(2).setCellValue(3000);
            returnRow.createCell(3).setCellFormula("B4*C4");
            sheet.createRow(4).createCell(0).setCellValue("날짜");

            assertTrue(StatementFinalBillingPatchService.writeItemSummaryValues(
                    sheet,
                    Map.of("일반콩나물", new BigDecimal("39"), "회수통", new BigDecimal("65")),
                    Map.of("일반콩나물", new BigDecimal("450000"), "회수통", new BigDecimal("-195000"))
            ));
            assertEquals(CellType.NUMERIC, general.getCell(3).getCellType());
            assertEquals(450_000d, general.getCell(3).getNumericCellValue());
            assertEquals(450_000d / 39, general.getCell(2).getNumericCellValue(), 0.01);
            assertEquals(195_000d, returnRow.getCell(3).getNumericCellValue());
            assertEquals(3000d, returnRow.getCell(2).getNumericCellValue());
        }
    }
}
