package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.ManualBookkeepingMonthlyEntity;
import com.example.salesmgmt.entity.ManualJournalEntryEntity;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManualBookkeepingExcelServiceTest {
    private final ManualBookkeepingExcelService service = new ManualBookkeepingExcelService();

    @Test
    void exportsManualAmountsToCorrectMonthsAndPreservesJournalDebitsAndCredits() throws Exception {
        byte[] data = service.export(2027,
                List.of(
                    new ManualBookkeepingMonthlyEntity(2027, 1, "SALES", new BigDecimal("1200000")),
                    new ManualBookkeepingMonthlyEntity(2027, 2, "SALES", new BigDecimal("2200000")),
                    new ManualBookkeepingMonthlyEntity(2027, 1, "ELECTRICITY", new BigDecimal("500000"))
                ),
                List.of(new ManualJournalEntryEntity(
                        LocalDate.of(2027, 1, 15), "전기료 결제",
                        "전력비", "보통예금", new BigDecimal("500000"))));
        assertTrue(data.length > 1500);
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            assertEquals(4, book.getNumberOfSheets());
            assertEquals(1200000d, book.getSheet("월별금액입력").getRow(4).getCell(1).getNumericCellValue());
            assertEquals(2200000d, book.getSheet("월별금액입력").getRow(4).getCell(2).getNumericCellValue());
            assertEquals("SUM(B5:M5)", book.getSheet("월별금액입력").getRow(4).getCell(13).getCellFormula());
            assertEquals("전력비", book.getSheet("분개장").getRow(4).getCell(3).getStringCellValue());
            assertEquals(500000d, book.getSheet("분개장").getRow(4).getCell(4).getNumericCellValue());
            assertEquals("보통예금", book.getSheet("분개장").getRow(5).getCell(3).getStringCellValue());
            assertEquals(500000d, book.getSheet("분개장").getRow(5).getCell(5).getNumericCellValue());
        }
    }

    @Test
    void exportsEmptyBookWithoutError() throws Exception {
        byte[] data = service.export(2026, List.of(), List.of());
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            assertNotNull(book.getSheet("월별금액입력"));
            assertNotNull(book.getSheet("분개장"));
            assertNotNull(book.getSheet("분개계정별집계"));
        }
    }
}
