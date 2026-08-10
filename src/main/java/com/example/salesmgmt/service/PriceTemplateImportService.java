package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.ImportIssue;
import com.example.salesmgmt.domain.ItemCatalog;
import com.example.salesmgmt.domain.PriceImportResult;
import com.example.salesmgmt.domain.PriceImportRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PriceTemplateImportService {

    private static final int MAX_SCAN_ROW = 30;

    private final VendorRuleService vendorRuleService;

    public PriceTemplateImportService(VendorRuleService vendorRuleService) {
        this.vendorRuleService = vendorRuleService;
    }

    public PriceImportResult importTemplate(MultipartFile file) {
        validateFile(file);

        Map<String, PriceImportRow> extractedRows = new LinkedHashMap<>();
        List<ImportIssue> issues = new ArrayList<>();
        int sheetCount = 0;

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            for (Sheet sheet : workbook) {
                sheetCount++;
                parseSheet(sheet, extractedRows, issues);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "template.xlsx 파일을 읽는 중 오류가 발생했습니다.",
                    exception
            );
        }

        return new PriceImportResult(
                sheetCount,
                List.copyOf(extractedRows.values()),
                List.copyOf(issues)
        );
    }

    private void parseSheet(
            Sheet sheet,
            Map<String, PriceImportRow> extractedRows,
            List<ImportIssue> issues
    ) {
        String statementVendor = sheet.getSheetName().trim();
        String inputVendor = vendorRuleService.inputVendorName(statementVendor);
        int foundInSheet = 0;

        int lastRow = Math.min(sheet.getLastRowNum(), MAX_SCAN_ROW - 1);
        for (int rowIndex = 0; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            foundInSheet += extractNormalItem(
                    sheet,
                    row,
                    0,
                    3,
                    inputVendor,
                    statementVendor,
                    extractedRows,
                    issues
            );

            foundInSheet += extractNormalItem(
                    sheet,
                    row,
                    6,
                    9,
                    inputVendor,
                    statementVendor,
                    extractedRows,
                    issues
            );

            String leftLabel = readText(row.getCell(0));
            if ("회수통현황".equals(leftLabel.replaceAll("\\s+", ""))) {
                Row priceRow = sheet.getRow(rowIndex + 2);
                Cell priceCell = priceRow == null ? null : priceRow.getCell(3);
                BigDecimal price = readPrice(priceCell, sheet.getSheetName(), issues);

                if (isPositive(price)) {
                    addPrice(
                            inputVendor,
                            statementVendor,
                            "회수통",
                            price,
                            sheet,
                            priceCell,
                            leftLabel,
                            extractedRows,
                            issues
                    );
                    foundInSheet++;
                }
            }
        }

        if (foundInSheet == 0) {
            issues.add(new ImportIssue(
                    ImportIssue.IssueLevel.WARNING,
                    statementVendor,
                    "0원보다 큰 품목 단가를 찾지 못했습니다."
            ));
        }
    }

    private int extractNormalItem(
            Sheet sheet,
            Row row,
            int labelColumn,
            int priceColumn,
            String inputVendor,
            String statementVendor,
            Map<String, PriceImportRow> extractedRows,
            List<ImportIssue> issues
    ) {
        Cell labelCell = row.getCell(labelColumn);
        String originalLabel = readText(labelCell);
        String itemName = ItemCatalog.normalizeTemplateLabel(originalLabel);

        if (itemName == null) {
            return 0;
        }

        Cell priceCell = row.getCell(priceColumn);
        BigDecimal price = readPrice(priceCell, sheet.getSheetName(), issues);

        if (!isPositive(price)) {
            return 0;
        }

        addPrice(
                inputVendor,
                statementVendor,
                itemName,
                price,
                sheet,
                priceCell,
                originalLabel,
                extractedRows,
                issues
        );
        return 1;
    }

    private void addPrice(
            String inputVendor,
            String statementVendor,
            String itemName,
            BigDecimal price,
            Sheet sheet,
            Cell priceCell,
            String originalLabel,
            Map<String, PriceImportRow> extractedRows,
            List<ImportIssue> issues
    ) {
        String key = inputVendor + "\u0000" + itemName;
        String sourceCell = priceCell == null
                ? ""
                : priceCell.getAddress().formatAsString();

        PriceImportRow newRow = new PriceImportRow(
                inputVendor,
                statementVendor,
                itemName,
                price.stripTrailingZeros(),
                sheet.getSheetName(),
                sourceCell,
                originalLabel
        );

        PriceImportRow existing = extractedRows.putIfAbsent(key, newRow);
        if (existing != null && existing.unitPrice().compareTo(price) != 0) {
            issues.add(new ImportIssue(
                    ImportIssue.IssueLevel.ERROR,
                    sheet.getSheetName() + "!" + sourceCell,
                    itemName + " 단가가 한 거래처에서 서로 다르게 두 번 발견됐습니다. "
                            + existing.unitPrice().toPlainString()
                            + "원 / "
                            + price.toPlainString()
                            + "원"
            ));
        }
    }

    private BigDecimal readPrice(
            Cell cell,
            String sheetName,
            List<ImportIssue> issues
    ) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }

        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }

            if (cell.getCellType() == CellType.FORMULA
                    && cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }

            if (cell.getCellType() == CellType.STRING) {
                String text = cell.getStringCellValue()
                        .replace(",", "")
                        .trim();

                if (text.isBlank()) {
                    return null;
                }

                return new BigDecimal(text);
            }

            return null;
        } catch (RuntimeException exception) {
            issues.add(new ImportIssue(
                    ImportIssue.IssueLevel.ERROR,
                    sheetName + "!" + cell.getAddress().formatAsString(),
                    "단가를 숫자로 읽을 수 없습니다."
            ));
            return null;
        }
    }

    private String readText(Cell cell) {
        if (cell == null) {
            return "";
        }

        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }

        return cell.toString().trim();
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 template.xlsx 파일을 선택해주세요.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null
                || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException(".xlsx 형식의 파일만 업로드할 수 있습니다.");
        }
    }
}
