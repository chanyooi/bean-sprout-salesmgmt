package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.ItemCatalog;
import com.example.salesmgmt.domain.StatementWorkbookResult;
import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import com.example.salesmgmt.repository.VendorRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SingleVendorStatementWorkbookService {

    private static final String SUNSAN_STATEMENT_NAME = "선산식자재마트";

    private final VendorRepository vendorRepository;
    private final SalesItemRepository salesItemRepository;
    private final StatementTemplateStorageService statementTemplateStorageService;
    private final DataFormatter formatter = new DataFormatter(Locale.KOREA);

    public SingleVendorStatementWorkbookService(
            VendorRepository vendorRepository,
            SalesItemRepository salesItemRepository,
            StatementTemplateStorageService statementTemplateStorageService
    ) {
        this.vendorRepository = vendorRepository;
        this.salesItemRepository = salesItemRepository;
        this.statementTemplateStorageService = statementTemplateStorageService;
    }

    @Transactional(readOnly = true)
    public StatementWorkbookResult generate(Long vendorId, YearMonth month) {
        VendorEntity vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("거래처를 찾을 수 없습니다."));

        String statementName = normalize(vendor.getStatementName());
        if (statementName.isBlank()) {
            throw new IllegalArgumentException("이 거래처의 명세서명이 등록되어 있지 않습니다.");
        }

        StatementPeriod period = periodFor(statementName, month);
        List<SalesItemEntity> items = salesItemRepository.findForVendorPeriod(
                vendorId,
                period.start(),
                period.end()
        );

        try (
                InputStream input = statementTemplateStorageService.openCurrentTemplate();
                XSSFWorkbook workbook = new XSSFWorkbook(input);
                ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {
            XSSFSheet target = prepareTargetSheet(workbook, statementName);

            removeAllOtherSheets(workbook, target);
            patchSheetFromDatabase(target, statementName, period, items);

            workbook.setForceFormulaRecalculation(true);
            workbook.write(output);

            String filename = month.getYear()
                    + "년_"
                    + String.format("%02d", month.getMonthValue())
                    + "월_"
                    + safeFilename(statementName)
                    + "_명세서.xlsx";

            return new StatementWorkbookResult(
                    output.toByteArray(),
                    filename,
                    1,
                    items.isEmpty() ? 0 : 1,
                    0,
                    0
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    statementName + " 거래처 명세서 파일을 만드는 중 오류가 발생했습니다.",
                    exception
            );
        }
    }

    private XSSFSheet prepareTargetSheet(XSSFWorkbook workbook, String statementName) {
        XSSFSheet existing = workbook.getSheet(statementName);
        if (existing != null) {
            return existing;
        }

        int sourceIndex = bestTemplateSheetIndex(workbook);
        if (sourceIndex < 0) {
            throw new IllegalArgumentException("명세서 기본 양식 시트를 찾지 못했습니다.");
        }

        XSSFSheet source = workbook.getSheetAt(sourceIndex);
        String sourceName = source.getSheetName();
        XSSFSheet cloned = workbook.cloneSheet(sourceIndex);
        String safeSheetName = safeSheetName(statementName);
        workbook.setSheetName(workbook.getSheetIndex(cloned), safeSheetName);
        replaceExactText(cloned, sourceName, safeSheetName);
        return cloned;
    }

    private void removeAllOtherSheets(XSSFWorkbook workbook, XSSFSheet target) {
        int targetIndex = workbook.getSheetIndex(target);
        for (int index = workbook.getNumberOfSheets() - 1; index >= 0; index--) {
            if (index != targetIndex) {
                workbook.removeSheetAt(index);
                if (index < targetIndex) {
                    targetIndex--;
                }
            }
        }
    }

    private void patchSheetFromDatabase(
            XSSFSheet sheet,
            String statementName,
            StatementPeriod period,
            List<SalesItemEntity> items
    ) {
        int headerIndex = findHeaderRow(sheet);
        if (headerIndex < 0) {
            throw new IllegalArgumentException(statementName + " 명세서에서 날짜 헤더를 찾지 못했습니다.");
        }

        Row header = sheet.getRow(headerIndex);
        Map<String, Integer> itemColumns = detectItemColumns(header);
        int dataStart = headerIndex + 1;
        int sumRow = findSumRow(sheet, dataStart);
        int dataEnd = Math.min(sumRow - 1, dataStart + 30);
        int amountColumn = detectAmountColumn(sheet, dataStart, dataEnd, itemColumns);

        int periodRow = headerIndex <= 30 ? 2 : 6;
        Cell periodCell = getCell(getRow(sheet, periodRow), 0);
        periodCell.setBlank();
        periodCell.setCellValue(period.text());

        for (int rowIndex = dataStart; rowIndex <= dataEnd; rowIndex++) {
            Row row = getRow(sheet, rowIndex);
            getCell(row, 0).setBlank();
            for (Integer column : itemColumns.values()) {
                getCell(row, column).setBlank();
            }
            if (amountColumn >= 0) {
                getCell(row, amountColumn).setBlank();
            }
        }

        Map<LocalDate, Map<String, BigDecimal>> quantities = new HashMap<>();
        Map<LocalDate, BigDecimal> amounts = new HashMap<>();

        for (SalesItemEntity item : items) {
            LocalDate date = item.getSalesOrder().getDeliveryDate();
            String itemName = normalizeItem(item.getItemName());
            quantities.computeIfAbsent(date, ignored -> new HashMap<>())
                    .merge(itemName, item.getQuantity(), BigDecimal::add);
            if (item.getLineAmount() != null) {
                amounts.merge(date, item.getLineAmount(), BigDecimal::add);
            }
        }

        List<LocalDate> dates = period.start()
                .datesUntil(period.end().plusDays(1))
                .toList();

        if (dates.size() > dataEnd - dataStart + 1) {
            throw new IllegalArgumentException(statementName + " 명세서의 날짜 행이 부족합니다.");
        }

        for (int offset = 0; offset < dates.size(); offset++) {
            LocalDate date = dates.get(offset);
            Row row = getRow(sheet, dataStart + offset);
            getCell(row, 0).setCellValue(Date.valueOf(date));

            Map<String, BigDecimal> dayQuantities = quantities.getOrDefault(date, Map.of());
            for (Map.Entry<String, Integer> entry : itemColumns.entrySet()) {
                BigDecimal quantity = dayQuantities.get(entry.getKey());
                if (quantity != null && quantity.signum() != 0) {
                    getCell(row, entry.getValue()).setCellValue(quantity.doubleValue());
                }
            }

            if (amountColumn >= 0) {
                BigDecimal amount = amounts.getOrDefault(date, BigDecimal.ZERO);
                Cell amountCell = getCell(row, amountColumn);
                if (amount.signum() == 0) {
                    amountCell.setBlank();
                } else {
                    amountCell.setCellValue(amount.doubleValue());
                }
            }
        }

        if (amountColumn >= 0 && sumRow > dataStart) {
            Cell totalCell = getCell(getRow(sheet, sumRow), amountColumn);
            totalCell.setCellFormula(
                    "SUM(" + columnName(amountColumn) + (dataStart + 1)
                            + ":" + columnName(amountColumn) + (dataEnd + 1) + ")"
            );
        }
    }

    private int bestTemplateSheetIndex(XSSFWorkbook workbook) {
        int bestIndex = -1;
        int bestScore = -1;
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            XSSFSheet sheet = workbook.getSheetAt(index);
            if (sheet.getSheetName().startsWith("생성확인")) {
                continue;
            }
            int header = findHeaderRow(sheet);
            if (header < 0) {
                continue;
            }
            int score = detectItemColumns(sheet.getRow(header)).size();
            if (score > bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private int findHeaderRow(XSSFSheet sheet) {
        int limit = Math.min(sheet.getLastRowNum(), 50);
        for (int rowIndex = 0; rowIndex <= limit; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && "날짜".equals(formatted(row.getCell(0)))) {
                return rowIndex;
            }
        }
        return -1;
    }

    private int findSumRow(XSSFSheet sheet, int start) {
        int limit = Math.min(sheet.getLastRowNum(), start + 40);
        for (int rowIndex = start; rowIndex <= limit; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && "합계".equals(formatted(row.getCell(0)))) {
                return rowIndex;
            }
        }
        return start + 31;
    }

    private Map<String, Integer> detectItemColumns(Row header) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (header == null) {
            return result;
        }
        for (int column = 0; column < Math.max(header.getLastCellNum(), 0); column++) {
            String item = normalizeItem(formatted(header.getCell(column)));
            if (ItemCatalog.ALL_ITEMS.contains(item)) {
                result.putIfAbsent(item, column);
            }
        }
        return result;
    }

    private int detectAmountColumn(
            XSSFSheet sheet,
            int dataStart,
            int dataEnd,
            Map<String, Integer> itemColumns
    ) {
        int maxItemColumn = itemColumns.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        for (int rowIndex = dataStart; rowIndex <= Math.min(dataEnd, dataStart + 3); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (int column = Math.max(maxItemColumn + 1, 1);
                 column < Math.max(row.getLastCellNum(), 0);
                 column++) {
                Cell cell = row.getCell(column);
                if (cell != null && cell.getCellType() == CellType.FORMULA) {
                    return column;
                }
            }
        }

        Row header = sheet.getRow(dataStart - 1);
        if (header != null) {
            for (int column = maxItemColumn + 1;
                 column < Math.max(header.getLastCellNum(), 0);
                 column++) {
                String label = formatted(header.getCell(column));
                if (label.contains("금액") || label.contains("일계") || label.contains("합계")) {
                    return column;
                }
            }
        }
        return -1;
    }

    private void replaceExactText(XSSFSheet sheet, String oldText, String newText) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING
                        && oldText.equals(cell.getStringCellValue().trim())) {
                    cell.setCellValue(newText);
                }
            }
        }
    }

    private StatementPeriod periodFor(String statementName, YearMonth month) {
        if (SUNSAN_STATEMENT_NAME.equals(statementName)) {
            LocalDate start = month.minusMonths(1).atDay(26);
            LocalDate end = month.atDay(25);
            return new StatementPeriod(
                    start,
                    end,
                    start.getYear() + "년 " + start.getMonthValue() + "/26~"
                            + month.getYear() + "년 " + month.getMonthValue() + "/25"
            );
        }
        return new StatementPeriod(
                month.atDay(1),
                month.atEndOfMonth(),
                month.getYear() + "년 " + month.getMonthValue() + "월"
        );
    }

    private String normalizeItem(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = ItemCatalog.normalizeTemplateLabel(raw);
        return normalized == null ? raw.trim() : normalized;
    }

    private String formatted(Cell cell) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private Row getRow(XSSFSheet sheet, int index) {
        Row row = sheet.getRow(index);
        return row == null ? sheet.createRow(index) : row;
    }

    private Cell getCell(Row row, int index) {
        Cell cell = row.getCell(index);
        return cell == null ? row.createCell(index) : cell;
    }

    private String columnName(int index) {
        int value = index + 1;
        StringBuilder builder = new StringBuilder();
        while (value > 0) {
            int mod = (value - 1) % 26;
            builder.insert(0, (char) ('A' + mod));
            value = (value - 1) / 26;
        }
        return builder.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeFilename(String value) {
        String safe = value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return safe.isBlank() ? "거래처" : safe;
    }

    private String safeSheetName(String value) {
        String safe = value.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (safe.isBlank()) {
            safe = "거래처";
        }
        return safe.length() > 31 ? safe.substring(0, 31) : safe;
    }

    private record StatementPeriod(LocalDate start, LocalDate end, String text) {}
}
