package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.SalesOrderEntity;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import com.example.salesmgmt.repository.VendorRepository;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class StatementWorkbookOnePassPrototypeServiceTest {

    @Mock
    SalesItemRepository salesItemRepository;

    @Mock
    VendorRepository vendorRepository;

    @Test
    void 원본_템플릿을_한번만_열어도_DB수량과_금액을_반영한_xlsx를_만들수있다() throws Exception {
        StatementWorkbookOnePassPrototypeService service =
                new StatementWorkbookOnePassPrototypeService(
                        salesItemRepository,
                        vendorRepository
                );

        VendorEntity vendor = new VendorEntity(
                "HS식자재도매유통",
                "HS식자재도매유통",
                true
        );

        SalesOrderEntity order = new SalesOrderEntity(
                "20260701-001",
                LocalDate.of(2026, 7, 1),
                vendor,
                null,
                "",
                "",
                "20260701",
                5
        );

        SalesItemEntity returnItem = new SalesItemEntity(
                order,
                "회수통",
                new BigDecimal("2"),
                new BigDecimal("-3000")
        );

        when(salesItemRepository.findForMonthlyReport(any(), any()))
                .thenReturn(List.of(returnItem));
        when(vendorRepository.findAllByOrderByInputNameAsc())
                .thenReturn(List.of(vendor));

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

        assertThat(result.filename()).isEqualTo("2026년_07월_월간명세서.xlsx");
        assertThat(result.fileBytes()).isNotEmpty();

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(result.fileBytes())
        )) {
            XSSFSheet sheet = workbook.getSheet("HS식자재도매유통");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(32).getCell(5).getNumericCellValue())
                    .isEqualTo(2d);
            assertThat(sheet.getRow(32).getCell(11).getNumericCellValue())
                    .isEqualTo(-6000d);
        }
    }

    @Test
    void 템플릿에_없는_거래처도_시트를_복제해서_한번의_write로_생성할수있다() throws Exception {
        StatementWorkbookOnePassPrototypeService service =
                new StatementWorkbookOnePassPrototypeService(
                        salesItemRepository,
                        vendorRepository
                );

        VendorEntity templateVendor = new VendorEntity(
                "기준거래처",
                "기준거래처",
                true
        );
        VendorEntity newVendor = new VendorEntity(
                "새거래처",
                "새거래처",
                true
        );

        SalesOrderEntity order = new SalesOrderEntity(
                "20260702-001",
                LocalDate.of(2026, 7, 2),
                newVendor,
                null,
                "",
                "",
                "20260702",
                5
        );
        SalesItemEntity item = new SalesItemEntity(
                order,
                "3.5kg일반",
                BigDecimal.ONE,
                new BigDecimal("3000")
        );

        when(salesItemRepository.findForMonthlyReport(any(), any()))
                .thenReturn(List.of(item));
        when(vendorRepository.findAllByOrderByInputNameAsc())
                .thenReturn(List.of(templateVendor, newVendor));

        MockMultipartFile template = new MockMultipartFile(
                "templateFile",
                "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createTemplate("기준거래처")
        );

        var result = service.generate(
                template,
                YearMonth.of(2026, 7),
                false
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(result.fileBytes())
        )) {
            XSSFSheet sheet = workbook.getSheet("새거래처");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(33).getCell(6).getNumericCellValue())
                    .isEqualTo(1d);
            assertThat(sheet.getRow(33).getCell(11).getNumericCellValue())
                    .isEqualTo(3000d);
        }
    }

    private byte[] createTemplate() throws Exception {
        return createTemplate("HS식자재도매유통");
    }

    private byte[] createTemplate(String sheetName) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet(sheetName);

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
                                + "-F" + (rowIndex + 1) + "*3000"
                );
            }

            workbook.write(output);
            return output.toByteArray();
        }
    }
}
