package com.example.salesmgmt.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.math.BigDecimal;

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
}
