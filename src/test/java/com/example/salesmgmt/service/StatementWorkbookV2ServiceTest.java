package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.StatementWorkbookResult;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementWorkbookV2ServiceTest {

    @Mock
    StatementWorkbookService legacyService;

    @Mock
    SalesItemRepository salesItemRepository;

    @Mock
    VendorRepository vendorRepository;

    @InjectMocks
    StatementWorkbookV2Service service;

    @Test
    void 회수통은_DB의_실제_음수금액으로_명세서_금액을_쓴다() throws Exception {
        SalesItemEntity returnItem = salesItem("2", "-3000");
        MockMultipartFile template = templateFile();
        byte[] templateBytes = createTemplate();

        when(legacyService.generate(any(), eq(YearMonth.of(2026, 7)), eq(true)))
                .thenReturn(new StatementWorkbookResult(
                        templateBytes,
                        "2026년_07월_월간명세서.xlsx",
                        1,
                        1,
                        0,
                        0
                ));
        when(salesItemRepository.findForMonthlyReport(any(), any()))
                .thenReturn(List.of(returnItem));
        when(vendorRepository.findAllByOrderByInputNameAsc())
                .thenReturn(List.of());

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
            assertThat(sheet.getRow(32).getCell(11).getNumericCellValue())
                    .isEqualTo(-6000d);
        }
    }

    @Test
    void 회수통_보증금이_없는_거래처는_명세서_금액을_차감하지_않는다() throws Exception {
        SalesItemEntity returnItem = salesItem("2", "0");
        MockMultipartFile template = templateFile();
        byte[] templateBytes = createTemplate();

        when(legacyService.generate(any(), eq(YearMonth.of(2026, 7)), eq(true)))
                .thenReturn(new StatementWorkbookResult(
                        templateBytes,
                        "2026년_07월_월간명세서.xlsx",
                        1,
                        1,
                        0,
                        0
                ));
        when(salesItemRepository.findForMonthlyReport(any(), any()))
                .thenReturn(List.of(returnItem));
        when(vendorRepository.findAllByOrderByInputNameAsc())
                .thenReturn(List.of());

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
            assertThat(sheet.getRow(32).getCell(11).getCellType())
                    .isEqualTo(CellType.BLANK);
        }
    }

    private MockMultipartFile templateFile() throws Exception {
        return new MockMultipartFile(
                "templateFile",
                "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createTemplate()
        );
    }

    private byte[] createTemplate() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("HS식자재도매유통");

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
            String quantity,
            String unitPrice
    ) {
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

        return new SalesItemEntity(
                order,
                "회수통",
                new BigDecimal(quantity),
                new BigDecimal(unitPrice)
        );
    }
}
