package com.example.salesmgmt.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementFinalBillingPatchServiceTest {

    @Test
    void finalBillingReplacesFormulaInsteadOfOnlyItsCachedValue() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("test");
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("최종 청구금액");
            var total = row.createCell(1);
            total.setCellFormula("1+1");
            assertTrue(StatementFinalBillingPatchService.writeFinalBillingAmount(sheet, new BigDecimal("20000")));
            org.junit.jupiter.api.Assertions.assertEquals(org.apache.poi.ss.usermodel.CellType.NUMERIC, total.getCellType());
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            org.junit.jupiter.api.Assertions.assertEquals(20000, total.getNumericCellValue());
        }
    }

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
}
