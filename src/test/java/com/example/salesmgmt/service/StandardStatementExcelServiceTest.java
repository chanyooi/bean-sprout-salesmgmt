package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.StandardStatementSchema;
import com.example.salesmgmt.domain.StandardStatementSchema.Kind;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StandardStatementExcelServiceTest {
    @Test
    void includesThreeStructuredFormsAndKeepsOldManualSheets() throws Exception {
        ManualBookkeepingExcelService previous = new ManualBookkeepingExcelService();
        byte[] old = previous.export(2025, List.of(), List.of());
        StandardStatementService entries = mock(StandardStatementService.class);
        when(entries.entered(2025)).thenReturn(Map.of(
                StandardStatementSchema.key(Kind.COST, "03"), new BigDecimal("321716000"),
                StandardStatementSchema.key(Kind.INCOME, "03"), new BigDecimal("485365400"),
                StandardStatementSchema.key(Kind.BALANCE, "59"), new BigDecimal("50000000")
        ));
        byte[] updated = new StandardStatementExcelService(entries).addStandardSheets(2025, old);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(updated))) {
            assertEquals(8, workbook.getNumberOfSheets());
            assertEquals("표준재무상태표", workbook.getSheetAt(0).getSheetName());
            assertEquals("표준손익계산서", workbook.getSheetAt(1).getSheetName());
            assertEquals("표준원가명세서", workbook.getSheetAt(2).getSheetName());
            assertNotNull(workbook.getSheet("월별금액입력"));
            assertNotNull(workbook.getSheet("분개장"));

            Sheet cost = workbook.getSheet("표준원가명세서");
            assertEquals("03", cost.getRow(7).getCell(0).getStringCellValue());
            assertEquals(321716000d, cost.getRow(7).getCell(2).getNumericCellValue());
            assertEquals("C7+C8-C9-C10", cost.getRow(5).getCell(2).getCellFormula());

            Sheet income = workbook.getSheet("표준손익계산서");
            assertTrue(income.getRow(21).getCell(2).getCellFormula().contains("'표준원가명세서'!C47"));
            Sheet balance = workbook.getSheet("표준재무상태표");
            assertTrue(balance.getRow(93).getCell(2).getCellFormula().contains("'표준손익계산서'!C104"));

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            CellValue computedCost = evaluator.evaluate(cost.getRow(46).getCell(2));
            assertEquals(321716000d, computedCost.getNumberValue());
        }
    }
}
