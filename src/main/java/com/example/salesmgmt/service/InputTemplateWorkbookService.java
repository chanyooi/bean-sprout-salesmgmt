package com.example.salesmgmt.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class InputTemplateWorkbookService {

    private static final String MASTER_RESOURCE =
            "templates/input_data_master.xlsx";
    private static final DateTimeFormatter SHEET_DATE_FORMAT =
            DateTimeFormatter.BASIC_ISO_DATE;

    private static final int FIRST_VENDOR_ROW_INDEX = 4; // Excel 5행
    private static final int LAST_VENDOR_ROW_INDEX = 79; // Excel 80행
    private static final int DELIVERY_DATE_COLUMN_INDEX = 1; // B
    private static final int VENDOR_COLUMN_INDEX = 2; // C
    private static final int FIRST_INPUT_COLUMN_INDEX = 3; // D
    private static final int LAST_INPUT_COLUMN_INDEX = 15; // P

    public byte[] createBlankWorkbook(YearMonth month) {
        if (month == null) {
            throw new IllegalArgumentException("다운로드할 월을 선택해주세요.");
        }

        ClassPathResource resource = new ClassPathResource(MASTER_RESOURCE);

        try (InputStream inputStream = resource.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            int daysInMonth = month.lengthOfMonth();

            if (workbook.getNumberOfSheets() < daysInMonth) {
                throw new IllegalStateException(
                        "input_data 원본 시트 수가 부족합니다."
                );
            }

            removeUnusedDaySheets(workbook, daysInMonth);

            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate date = month.atDay(day);
                Sheet sheet = workbook.getSheetAt(day - 1);
                prepareDaySheet(workbook, sheet, day - 1, date);
            }

            // A열 주문번호와 B6:B80 날짜는 원본 엑셀의 수식을 그대로 유지합니다.
            // B5 날짜만 바꾸면 나머지 값이 자동 계산됩니다.
            // Excel이 새 월 값으로 수식을 다시 계산하도록 강제합니다.
            workbook.setForceFormulaRecalculation(true);
            workbook.setActiveSheet(0);
            workbook.setSelectedTab(0);
            workbook.write(outputStream);

            // 기존 원본에는 계산 체인(calcChain)이 들어 있습니다.
            // 월 변경/시트 삭제 뒤 오래된 calcChain이 남아 있으면 Excel에서
            // "내용에 문제가 있습니다" 복구창이 뜰 수 있으므로 제거합니다.
            // calcChain은 없어도 Excel이 정상적으로 수식을 다시 계산합니다.
            return removeCalculationChain(outputStream.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "input_data.xlsx 생성 중 오류가 발생했습니다.",
                    exception
            );
        }
    }

    private void removeUnusedDaySheets(
            XSSFWorkbook workbook,
            int daysInMonth
    ) {
        for (int sheetIndex = workbook.getNumberOfSheets() - 1;
             sheetIndex >= daysInMonth;
             sheetIndex--) {
            workbook.removeSheetAt(sheetIndex);
        }
    }

    private void prepareDaySheet(
            XSSFWorkbook workbook,
            Sheet sheet,
            int sheetIndex,
            LocalDate date
    ) {
        String dateText = date.format(SHEET_DATE_FORMAT);
        workbook.setSheetName(sheetIndex, dateText);

        // 원본 장부는 B5만 실제 날짜이고, B6:B80은 B5를 참조하는 수식입니다.
        // 주문번호 A5:A80 역시 날짜를 참조하는 수식이므로 A/B 수식을 건드리지 않습니다.
        Row firstVendorRow = sheet.getRow(FIRST_VENDOR_ROW_INDEX);
        if (firstVendorRow == null) {
            throw new IllegalStateException(
                    sheet.getSheetName() + " 시트의 첫 거래처 행을 찾을 수 없습니다."
            );
        }

        Cell dateAnchorCell = getOrCreateCell(
                firstVendorRow,
                DELIVERY_DATE_COLUMN_INDEX
        );
        dateAnchorCell.setCellValue(Date.valueOf(date));

        for (int rowIndex = FIRST_VENDOR_ROW_INDEX;
             rowIndex <= LAST_VENDOR_ROW_INDEX;
             rowIndex++) {

            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            Cell vendorCell = row.getCell(VENDOR_COLUMN_INDEX);
            if (vendorCell == null || vendorCell.toString().isBlank()) {
                continue;
            }

            // 노란색 실제 입력 영역만 비웁니다.
            // 셀 스타일/데이터 유효성 검사/드롭다운은 그대로 유지됩니다.
            for (int columnIndex = FIRST_INPUT_COLUMN_INDEX;
                 columnIndex <= LAST_INPUT_COLUMN_INDEX;
                 columnIndex++) {
                getOrCreateCell(row, columnIndex).setBlank();
            }
        }
    }

    private Cell getOrCreateCell(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            cell = row.createCell(columnIndex);
        }
        return cell;
    }

    private byte[] removeCalculationChain(byte[] workbookBytes)
            throws IOException {

        try (ByteArrayInputStream inputStream =
                     new ByteArrayInputStream(workbookBytes);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                byte[] content = zipInputStream.readAllBytes();

                if ("xl/calcChain.xml".equals(entryName)) {
                    continue;
                }

                if ("xl/_rels/workbook.xml.rels".equals(entryName)) {
                    String xml = new String(content, StandardCharsets.UTF_8);
                    xml = xml.replaceAll(
                            "(?s)<Relationship\\b[^>]*(?:calcChain|relationships/calcChain)[^>]*/>",
                            ""
                    );
                    content = xml.getBytes(StandardCharsets.UTF_8);
                } else if ("[Content_Types].xml".equals(entryName)) {
                    String xml = new String(content, StandardCharsets.UTF_8);
                    xml = xml.replaceAll(
                            "(?s)<Override\\b[^>]*calcChain[^>]*/>",
                            ""
                    );
                    content = xml.getBytes(StandardCharsets.UTF_8);
                }

                ZipEntry newEntry = new ZipEntry(entryName);
                zipOutputStream.putNextEntry(newEntry);
                zipOutputStream.write(content);
                zipOutputStream.closeEntry();
            }

            zipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }
}
