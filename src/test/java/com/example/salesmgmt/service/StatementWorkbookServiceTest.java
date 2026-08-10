package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.SalesOrderEntity;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementWorkbookServiceTest {

    @Mock
    SalesItemRepository salesItemRepository;

    @InjectMocks
    StatementWorkbookService service;

    @Test
    void DB의_날짜별_수량을_템플릿에_입력한다() throws Exception {
        SalesItemEntity item = salesItem(
                "HS식자재도매유통",
                LocalDate.of(2026, 7, 31),
                "3.5kg일반",
                "50",
                "3000"
        );

        when(salesItemRepository.findForMonthlyReport(any(), any()))
                .thenReturn(List.of(item));

        MockMultipartFile template = new MockMultipartFile(
                "templateFile",
                "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createTemplate()
        );

        var result = service.generate(
                template,
                YearMonth.of(2026, 7),
                false
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(result.fileBytes())
        )) {
            XSSFSheet sheet = workbook.getSheet("HS식자재도매유통");

            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(6).getCell(0).getStringCellValue())
                    .isEqualTo("2026년 7월");
            assertThat(sheet.getRow(62).getCell(6).getNumericCellValue())
                    .isEqualTo(50d);
        }
    }

    @Test
    void 회수통은_DB의_실제_음수금액으로_명세서_합계를_보정한다() throws Exception {
        SalesItemEntity returnItem = salesItem(
                "HS식자재도매유통",
                LocalDate.of(2026, 7, 1),
                "회수통",
                "2",
                "-3000"
        );

        when(salesItemRepository.findForMonthlyReport(any(), any()))
                .thenReturn(List.of(returnItem));

        MockMultipartFile template = new MockMultipartFile(
                "templateFile",
                "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createTemplate()
        );

        var result = service.generate(
                template,
                YearMonth.of(2026, 7),
                false
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(result.fileBytes())
        )) {
            XSSFSheet sheet = workbook.getSheet("HS식자재도매유통");

            assertThat(sheet.getRow(32).getCell(5).getNumericCellValue())
                    .isEqualTo(2d);
            assertThat(sheet.getRow(32).getCell(11).getCellFormula())
                    .isEqualTo("G33*3000-6000");
        }
    }

    @Test
    void 회수통_보증금이_없는_거래처는_명세서에서도_차감하지_않는다() throws Exception {
        SalesItemEntity returnItem = salesItem(
                "HS식자재도매유통",
                LocalDate.of(2026, 7, 1),
                "회수통",
                "2",
                "0"
        );

        when(salesItemRepository.findForMonthlyReport(any(), any()))
                .thenReturn(List.of(returnItem));

        MockMultipartFile template = new MockMultipartFile(
                "templateFile",
                "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createTemplate()
        );

        var result = service.generate(
                template,
                YearMonth.of(2026, 7),
                false
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(result.fileBytes())
        )) {
            XSSFSheet sheet = workbook.getSheet("HS식자재도매유통");

            assertThat(sheet.getRow(32).getCell(11).getCellFormula())
                    .isEqualTo("G33*3000");
        }
    }

    @Test
    void 판매가_없는_시트는_선택에_따라_제거한다() throws Exception {
        when(salesItemRepository.findForMonthlyReport(any(), any()))
                .thenReturn(List.of());

        MockMultipartFile template = new MockMultipartFile(
                "templateFile",
                "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createTemplate()
        );

        var result = service.generate(
                template,
                YearMonth.of(2026, 7),
                true
        );

        assertThat(result.generatedSheetCount()).isEqualTo(1);
        assertThat(result.sheetWithSalesCount()).isZero();
    }

    private byte[] createTemplate() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("HS식자재도매유통");
            sheet.addMergedRegion(
                    new org.apache.poi.ss.util.CellRangeAddress(6, 7, 0, 3)
            );

            Row periodRow = sheet.createRow(6);
            periodRow.createCell(0).setCellValue("기존 기간");

            Row headerRow = sheet.createRow(31);
            headerRow.createCell(0).setCellValue("날짜");
            headerRow.createCell(5).setCellValue("회수통");
            headerRow.createCell(6).setCellValue("3.5kg일반");
            headerRow.createCell(11).setCellValue("합계");

            for (int rowIndex = 32; rowIndex <= 62; rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                row.createCell(0, CellType.BLANK);
                row.createCell(5, CellType.BLANK);
                row.createCell(6, CellType.BLANK);
                row.createCell(11).setCellFormula(
                        "G" + (rowIndex + 1) + "*3000"
                                + "-F" + (rowIndex + 1) + "*5500"
                );
            }

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private SalesItemEntity salesItem(
            String vendorName,
            LocalDate date,
            String itemName,
            String quantity,
            String unitPrice
    ) {
        VendorEntity vendor = new VendorEntity(
                vendorName,
                vendorName,
                true
        );

        SalesOrderEntity order = new SalesOrderEntity(
                "20260731-001",
                date,
                vendor,
                null,
                "",
                "",
                "20260731",
                5
        );

        return new SalesItemEntity(
                order,
                itemName,
                new BigDecimal(quantity),
                new BigDecimal(unitPrice)
        );
    }
}
