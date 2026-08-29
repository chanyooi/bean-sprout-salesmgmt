package com.example.salesmgmt.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /**
     * 기존 내부 호출 호환용입니다. 새 다운로드 경로는 createBlankWorkbookFile을 사용해
     * 대용량 byte[]를 오래 메모리에 들고 있지 않습니다.
     */
    public byte[] createBlankWorkbook(YearMonth month) {
        Path file = createBlankWorkbookFile(month);
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "input_data.xlsx 파일을 읽는 중 오류가 발생했습니다.",
                    exception
            );
        } finally {
            deleteQuietly(file);
        }
    }

    /**
     * XSSFWorkbook 결과를 ByteArrayOutputStream에 통째로 복사하지 않고 임시 파일에
     * 바로 기록합니다. Railway처럼 메모리가 제한된 환경에서 큰 엑셀 다운로드 때
     * 순간 메모리 사용량이 치솟는 문제를 줄이기 위한 경로입니다.
     */
    public Path createBlankWorkbookFile(YearMonth month) {
        if (month == null) {
            throw new IllegalArgumentException("다운로드할 월을 선택해주세요.");
        }

        ClassPathResource resource = new ClassPathResource(MASTER_RESOURCE);
        Path generated = null;

        try {
            generated = Files.createTempFile("input-data-generated-", ".xlsx");

            try (InputStream inputStream = resource.getInputStream();
                 XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
                 OutputStream outputStream = Files.newOutputStream(generated)) {

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

                workbook.setForceFormulaRecalculation(true);
                workbook.setActiveSheet(0);
                workbook.setSelectedTab(0);
                workbook.write(outputStream);
            }

            Path cleaned = removeCalculationChain(generated);
            deleteQuietly(generated);
            return cleaned;
        } catch (IOException exception) {
            deleteQuietly(generated);
            throw new IllegalStateException(
                    "input_data.xlsx 생성 중 오류가 발생했습니다.",
                    exception
            );
        } catch (RuntimeException exception) {
            deleteQuietly(generated);
            throw exception;
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

    /**
     * ZIP 전체를 byte[]로 한 번 더 복제하지 않고 파일→파일로 스트리밍합니다.
     * 수정이 필요한 작은 XML 엔트리만 메모리에서 처리합니다.
     */
    private Path removeCalculationChain(Path workbookFile) throws IOException {
        Path cleaned = Files.createTempFile("input-data-clean-", ".xlsx");

        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(workbookFile));
             ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(cleaned))) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();

                if ("xl/calcChain.xml".equals(entryName)) {
                    zipInputStream.closeEntry();
                    continue;
                }

                ZipEntry newEntry = new ZipEntry(entryName);
                zipOutputStream.putNextEntry(newEntry);

                if ("xl/_rels/workbook.xml.rels".equals(entryName)
                        || "[Content_Types].xml".equals(entryName)) {
                    byte[] content = zipInputStream.readAllBytes();
                    String xml = new String(content, StandardCharsets.UTF_8);

                    if ("xl/_rels/workbook.xml.rels".equals(entryName)) {
                        xml = xml.replaceAll(
                                "(?s)<Relationship\\b[^>]*(?:calcChain|relationships/calcChain)[^>]*/>",
                                ""
                        );
                    } else {
                        xml = xml.replaceAll(
                                "(?s)<Override\\b[^>]*calcChain[^>]*/>",
                                ""
                        );
                    }
                    zipOutputStream.write(xml.getBytes(StandardCharsets.UTF_8));
                } else {
                    zipInputStream.transferTo(zipOutputStream);
                }

                zipOutputStream.closeEntry();
                zipInputStream.closeEntry();
            }

            zipOutputStream.finish();
            return cleaned;
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(cleaned);
            throw exception;
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 임시 파일 정리는 다음 재시작 시 OS가 처리할 수 있으므로 본 작업은 실패시키지 않습니다.
        }
    }
}
