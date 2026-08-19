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
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StatementWorkbookV2Service {

    private static final String SUNSAN_STATEMENT_NAME = "선산식자재마트";

    private final StatementWorkbookService legacyService;
    private final SalesItemRepository salesItemRepository;
    private final VendorRepository vendorRepository;
    private final DataFormatter formatter = new DataFormatter(Locale.KOREA);

    public StatementWorkbookV2Service(
            StatementWorkbookService legacyService,
            SalesItemRepository salesItemRepository,
            VendorRepository vendorRepository
    ) {
        this.legacyService = legacyService;
        this.salesItemRepository = salesItemRepository;
        this.vendorRepository = vendorRepository;
    }

    @Transactional(readOnly = true)
    public StatementWorkbookResult generate(
            MultipartFile templateFile,
            YearMonth month,
            boolean includeEmptySheets
    ) {
        // 항상 원본 템플릿 시트를 보존한 상태로 시작해야 새 거래처 시트를 복제할 수 있다.
        StatementWorkbookResult base = legacyService.generate(
                templateFile,
                month,
                true
        );

        LocalDate queryStart = month.minusMonths(1).atDay(26);
        LocalDate queryEnd = month.atEndOfMonth();
        List<SalesItemEntity> allItems = salesItemRepository.findForMonthlyReport(
                queryStart,
                queryEnd
        );

        Map<String, List<SalesItemEntity>> byStatement = new LinkedHashMap<>();
        for (SalesItemEntity item : allItems) {
            String name = normalize(item.getSalesOrder().getVendor().getStatementName());
            byStatement.computeIfAbsent(name, ignored -> new ArrayList<>()).add(item);
        }

        try (
                XSSFWorkbook workbook = new XSSFWorkbook(
                        new ByteArrayInputStream(base.fileBytes())
                );
                ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {
            removeGeneratedWarningSheets(workbook);
            createMissingVendorSheets(workbook, byStatement, includeEmptySheets, month);

            int sheetsWithSales = 0;
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                XSSFSheet sheet = workbook.getSheetAt(i);
                String statementName = normalize(sheet.getSheetName());
                List<SalesItemEntity> periodItems = periodItems(
                        statementName,
                        month,
                        byStatement
                );

                if (!periodItems.isEmpty()) {
                    sheetsWithSales++;
                }

                patchSheetFromDatabase(
                        sheet,
                        statementName,
                        periodFor(statementName, month),
                        periodItems
                );
            }

            int removedEmpty = 0;
            if (!includeEmptySheets) {
                for (int i = workbook.getNumberOfSheets() - 1; i >= 0; i--) {
                    String statementName = normalize(workbook.getSheetName(i));
                    if (periodItems(statementName, month, byStatement).isEmpty()) {
                        workbook.removeSheetAt(i);
                        removedEmpty++;
                    }
                }
            }

            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException(
                        month + "에 생성할 명세서가 없습니다. 빈 명세서 포함을 선택하거나 판매자료를 확인해주세요."
                );
            }

            workbook.setForceFormulaRecalculation(true);
            workbook.write(output);

            return new StatementWorkbookResult(
                    output.toByteArray(),
                    base.filename(),
                    workbook.getNumberOfSheets(),
                    sheetsWithSales,
                    removedEmpty,
                    0
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "DB 단가를 명세서에 반영하는 중 오류가 발생했습니다.",
                    exception
            );
        }
    }

    private List<SalesItemEntity> periodItems(
            String statementName,
            YearMonth month,
            Map<String, List<SalesItemEntity>> byStatement
    ) {
        StatementPeriod period = periodFor(statementName, month);
        return byStatement.getOrDefault(statementName, List.of())
                .stream()
                .filter(item -> within(
                        item.getSalesOrder().getDeliveryDate(),
                        period.start(),
                        period.end()
                ))
                .toList();
    }

    private void createMissingVendorSheets(
            XSSFWorkbook workbook,
            Map<String, List<SalesItemEntity>> byStatement,
            boolean includeEmptySheets,
            YearMonth month
    ) {
        Map<String, VendorEntity> wanted = new LinkedHashMap<>();
        for (VendorEntity vendor : vendorRepository.findAllByOrderByInputNameAsc()) {
            String name = normalize(vendor.getStatementName());
            boolean hasSales = !periodItems(name, month, byStatement).isEmpty();
            if (includeEmptySheets || hasSales) {
                wanted.put(name, vendor);
            }
        }

        int templateIndex = bestTemplateSheetIndex(workbook);
        if (templateIndex < 0) {
            return;
        }

        List<String> existing = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            existing.add(normalize(workbook.getSheetName(i)));
        }

        for (String statementName : wanted.keySet()) {
            if (statementName.isBlank() || existing.contains(statementName)) {
                continue;
            }

            XSSFSheet source = workbook.getSheetAt(templateIndex);
            String sourceName = source.getSheetName();
            XSSFSheet cloned = workbook.cloneSheet(templateIndex);
            String safeName = uniqueSheetName(workbook, statementName);
            workbook.setSheetName(workbook.getSheetIndex(cloned), safeName);
            replaceExactText(cloned, sourceName, safeName);
            existing.add(statementName);
        }
    }

    private int bestTemplateSheetIndex(XSSFWorkbook workbook) {
        int bestIndex = -1;
        int bestScore = -1;
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            XSSFSheet sheet = workbook.getSheetAt(i);
            if (isWarningSheet(sheet.getSheetName())) {
                continue;
            }
            int header = findHeaderRow(sheet);
            if (header < 0) {
                continue;
            }
            int score = detectItemColumns(sheet.getRow(header)).size();
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private void patchSheetFromDatabase(
            XSSFSheet sheet,
            String statementName,
            StatementPeriod period,
            List<SalesItemEntity> items
    ) {
        int headerIndex = findHeaderRow(sheet);
        if (headerIndex < 0) {
            return;
        }

        Row header = sheet.getRow(headerIndex);
        Map<String, Integer> itemColumns = detectItemColumns(header);
        int dataStart = headerIndex + 1;
        int sumRow = findSumRow(sheet, dataStart);
        int dataEnd = Math.min(sumRow - 1, dataStart + 30);
        int amountColumn = detectAmountColumn(sheet, dataStart, dataEnd, itemColumns);

        int periodRow = headerIndex <= 30 ? 2 : 6;
        getCell(getRow(sheet, periodRow), 0).setCellValue(period.text());

        for (int r = dataStart; r <= dataEnd; r++) {
            Row row = getRow(sheet, r);
            getCell(row, 0).setBlank();
            for (Integer c : itemColumns.values()) {
                getCell(row, c).setBlank();
            }
            if (amountColumn >= 0) {
                getCell(row, amountColumn).setBlank();
            }
        }

        Map<LocalDate, Map<String, BigDecimal>> qty = new HashMap<>();
        Map<LocalDate, BigDecimal> amount = new HashMap<>();
        for (SalesItemEntity item : items) {
            LocalDate date = item.getSalesOrder().getDeliveryDate();
            String normalizedItem = normalizeItem(item.getItemName());
            qty.computeIfAbsent(date, ignored -> new HashMap<>())
                    .merge(normalizedItem, item.getQuantity(), BigDecimal::add);
            if (item.getLineAmount() != null) {
                amount.merge(date, item.getLineAmount(), BigDecimal::add);
            }
        }

        List<LocalDate> dates = period.start().datesUntil(period.end().plusDays(1)).toList();
        if (dates.size() > dataEnd - dataStart + 1) {
            throw new IllegalArgumentException(
                    statementName + " 명세서의 날짜 행이 부족합니다."
            );
        }

        for (int offset = 0; offset < dates.size(); offset++) {
            LocalDate date = dates.get(offset);
            Row row = getRow(sheet, dataStart + offset);
            getCell(row, 0).setCellValue(Date.valueOf(date));

            Map<String, BigDecimal> dayQty = qty.getOrDefault(date, Map.of());
            for (Map.Entry<String, Integer> col : itemColumns.entrySet()) {
                BigDecimal value = dayQty.get(col.getKey());
                if (value != null && value.signum() != 0) {
                    getCell(row, col.getValue()).setCellValue(value.doubleValue());
                }
            }

            if (amountColumn >= 0) {
                BigDecimal dayAmount = amount.getOrDefault(date, BigDecimal.ZERO);
                Cell amountCell = getCell(row, amountColumn);
                if (dayAmount.signum() == 0) {
                    amountCell.setBlank();
                } else {
                    amountCell.setCellValue(dayAmount.doubleValue());
                }
            }
        }

        if (amountColumn >= 0 && sumRow > dataStart) {
            Cell totalCell = getCell(getRow(sheet, sumRow), amountColumn);
            String startRef = columnName(amountColumn) + (dataStart + 1);
            String endRef = columnName(amountColumn) + (dataEnd + 1);
            totalCell.setCellFormula("SUM(" + startRef + ":" + endRef + ")");
        }
    }

    private int detectAmountColumn(
            XSSFSheet sheet,
            int dataStart,
            int dataEnd,
            Map<String, Integer> itemColumns
    ) {
        int maxItem = itemColumns.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        for (int r = dataStart; r <= Math.min(dataEnd, dataStart + 3); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int c = Math.max(maxItem + 1, 1); c < Math.max(row.getLastCellNum(), 0); c++) {
                Cell cell = row.getCell(c);
                if (cell != null && cell.getCellType() == CellType.FORMULA) {
                    return c;
                }
            }
        }

        Row header = sheet.getRow(dataStart - 1);
        if (header != null) {
            for (int c = maxItem + 1; c < Math.max(header.getLastCellNum(), 0); c++) {
                String label = formatted(header.getCell(c));
                if (label.contains("금액") || label.contains("일계") || label.contains("합계")) {
                    return c;
                }
            }
        }
        return -1;
    }

    private int findHeaderRow(XSSFSheet sheet) {
        int limit = Math.min(sheet.getLastRowNum(), 50);
        for (int r = 0; r <= limit; r++) {
            Row row = sheet.getRow(r);
            if (row != null && "날짜".equals(formatted(row.getCell(0)))) {
                return r;
            }
        }
        return -1;
    }

    private int findSumRow(XSSFSheet sheet, int start) {
        int limit = Math.min(sheet.getLastRowNum(), start + 40);
        for (int r = start; r <= limit; r++) {
            Row row = sheet.getRow(r);
            if (row != null && "합계".equals(formatted(row.getCell(0)))) {
                return r;
            }
        }
        return start + 31;
    }

    private Map<String, Integer> detectItemColumns(Row header) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (header == null) {
            return result;
        }
        for (int c = 0; c < Math.max(header.getLastCellNum(), 0); c++) {
            String item = normalizeItem(formatted(header.getCell(c)));
            if (ItemCatalog.ALL_ITEMS.contains(item)) {
                result.putIfAbsent(item, c);
            }
        }
        return result;
    }

    private String normalizeItem(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = ItemCatalog.normalizeTemplateLabel(raw);
        return normalized == null ? raw.trim() : normalized;
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

    private void removeGeneratedWarningSheets(XSSFWorkbook workbook) {
        for (int i = workbook.getNumberOfSheets() - 1; i >= 0; i--) {
            if (isWarningSheet(workbook.getSheetName(i))) {
                workbook.removeSheetAt(i);
            }
        }
    }

    private boolean isWarningSheet(String name) {
        return name != null && name.startsWith("생성확인");
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

    private String uniqueSheetName(XSSFWorkbook workbook, String requested) {
        String base = requested.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (base.isBlank()) {
            base = "거래처";
        }
        if (base.length() > 31) {
            base = base.substring(0, 31);
        }
        String candidate = base;
        int number = 2;
        while (workbook.getSheet(candidate) != null) {
            String suffix = "(" + number++ + ")";
            candidate = base.substring(0, Math.min(base.length(), 31 - suffix.length())) + suffix;
        }
        return candidate;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean within(LocalDate date, LocalDate start, LocalDate end) {
        return date != null && !date.isBefore(start) && !date.isAfter(end);
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

    private record StatementPeriod(LocalDate start, LocalDate end, String text) {}
}
