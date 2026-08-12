package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.ItemCatalog;
import com.example.salesmgmt.domain.StatementWorkbookResult;
import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class StatementWorkbookService {

    private static final int PERIOD_ROW_INDEX = 6;
    private static final int HEADER_ROW_INDEX = 31;
    private static final int DATA_START_ROW_INDEX = 32;
    private static final int DATA_END_ROW_INDEX = 62;
    private static final int DATE_COLUMN_INDEX = 0;

    private static final String SUNSAN_STATEMENT_NAME = "선산식자재마트";
    private static final String WARNING_SHEET_NAME = "생성확인";

    private final SalesItemRepository salesItemRepository;
    private final DataFormatter dataFormatter = new DataFormatter(Locale.KOREA);

    public StatementWorkbookService(
            SalesItemRepository salesItemRepository
    ) {
        this.salesItemRepository = salesItemRepository;
    }

    @Transactional(readOnly = true)
    public StatementWorkbookResult generate(
            MultipartFile templateFile,
            YearMonth month,
            boolean includeEmptySheets
    ) {
        validateTemplate(templateFile);

        LocalDate normalStart = month.atDay(1);
        LocalDate normalEnd = month.atEndOfMonth();
        LocalDate sunsanStart = month.minusMonths(1).atDay(26);

        List<SalesItemEntity> allItems =
                salesItemRepository.findForMonthlyReport(
                        sunsanStart,
                        normalEnd
                );

        Map<String, List<SalesItemEntity>> itemsByStatementName =
                groupByStatementName(allItems);

        try (InputStream inputStream = templateFile.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            List<Integer> sheetIndexesToRemove = new ArrayList<>();
            List<GenerationWarning> warnings = new ArrayList<>();
            Set<String> templateStatementNames = new HashSet<>();
            int sheetWithSalesCount = 0;

            int originalSheetCount = workbook.getNumberOfSheets();

            for (int sheetIndex = 0;
                 sheetIndex < originalSheetCount;
                 sheetIndex++) {

                XSSFSheet sheet = workbook.getSheetAt(sheetIndex);
                String statementName = normalizeName(sheet.getSheetName());
                templateStatementNames.add(statementName);

                StatementPeriod period = statementPeriod(
                        statementName,
                        month,
                        normalStart,
                        normalEnd,
                        sunsanStart
                );

                List<SalesItemEntity> statementItems =
                        itemsByStatementName
                                .getOrDefault(statementName, List.of())
                                .stream()
                                .filter(item -> isWithinPeriod(
                                        item,
                                        period.startDate(),
                                        period.endDate()
                                ))
                                .toList();

                if (statementItems.isEmpty() && !includeEmptySheets) {
                    sheetIndexesToRemove.add(sheetIndex);
                    continue;
                }

                if (!statementItems.isEmpty()) {
                    sheetWithSalesCount++;
                }

                fillSheet(
                        sheet,
                        period,
                        statementItems,
                        warnings
                );
            }

            addMissingTemplateWarnings(
                    itemsByStatementName,
                    templateStatementNames,
                    month,
                    warnings
            );

            for (int index = sheetIndexesToRemove.size() - 1;
                 index >= 0;
                 index--) {
                workbook.removeSheetAt(sheetIndexesToRemove.get(index));
            }

            int generatedStatementSheetCount = workbook.getNumberOfSheets();

            if (generatedStatementSheetCount == 0) {
                throw new IllegalArgumentException(
                        month + "에 생성할 명세서가 없습니다. "
                                + "빈 명세서 포함을 선택하거나 판매자료를 확인해주세요."
                );
            }

            if (!warnings.isEmpty()) {
                createWarningSheet(workbook, warnings);
            }

            recalculateFormulas(workbook);
            workbook.write(outputStream);

            String filename = month.getYear()
                    + "년_"
                    + String.format("%02d", month.getMonthValue())
                    + "월_월간명세서.xlsx";

            return new StatementWorkbookResult(
                    outputStream.toByteArray(),
                    filename,
                    generatedStatementSheetCount,
                    sheetWithSalesCount,
                    sheetIndexesToRemove.size(),
                    warnings.size()
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "template.xlsx를 읽거나 명세서 파일을 만드는 중 오류가 발생했습니다.",
                    exception
            );
        }
    }

    private Map<String, List<SalesItemEntity>> groupByStatementName(
            List<SalesItemEntity> items
    ) {
        Map<String, List<SalesItemEntity>> grouped = new HashMap<>();

        for (SalesItemEntity item : items) {
            String statementName = normalizeName(
                    item.getSalesOrder()
                            .getVendor()
                            .getStatementName()
            );

            grouped.computeIfAbsent(
                    statementName,
                    ignored -> new ArrayList<>()
            ).add(item);
        }

        return grouped;
    }

    private StatementPeriod statementPeriod(
            String statementName,
            YearMonth month,
            LocalDate normalStart,
            LocalDate normalEnd,
            LocalDate sunsanStart
    ) {
        if (SUNSAN_STATEMENT_NAME.equals(statementName)) {
            return new StatementPeriod(
                    sunsanStart,
                    month.atDay(25),
                    sunsanStart.getYear()
                            + "년 "
                            + sunsanStart.getMonthValue()
                            + "/"
                            + sunsanStart.getDayOfMonth()
                            + "~"
                            + month.getYear()
                            + "년 "
                            + month.getMonthValue()
                            + "/25"
            );
        }

        return new StatementPeriod(
                normalStart,
                normalEnd,
                month.getYear() + "년 " + month.getMonthValue() + "월"
        );
    }

    private void fillSheet(
            XSSFSheet sheet,
            StatementPeriod period,
            List<SalesItemEntity> items,
            List<GenerationWarning> warnings
    ) {
        writePeriod(sheet, period.periodText());

        Map<String, Integer> itemColumns = detectItemColumns(sheet);
        clearDataArea(sheet, itemColumns);

        addMissingItemColumnWarnings(
                sheet.getSheetName(),
                items,
                itemColumns.keySet(),
                warnings
        );

        Map<LocalDate, Map<String, BigDecimal>> quantityPivot =
                createQuantityPivot(items);
        Map<LocalDate, BigDecimal> returnContainerAmountByDate =
                createReturnContainerAmountByDate(items);

        List<LocalDate> dates = period.startDate()
                .datesUntil(period.endDate().plusDays(1))
                .toList();

        if (dates.size() > 31) {
            throw new IllegalArgumentException(
                    sheet.getSheetName()
                            + " 명세서 기간이 31일을 초과합니다."
            );
        }

        for (int offset = 0; offset < dates.size(); offset++) {
            LocalDate date = dates.get(offset);
            int rowIndex = DATA_START_ROW_INDEX + offset;
            Row row = getOrCreateRow(sheet, rowIndex);

            Cell dateCell = getOrCreateCell(row, DATE_COLUMN_INDEX);
            dateCell.setCellValue(Date.valueOf(date));

            Map<String, BigDecimal> quantities =
                    quantityPivot.getOrDefault(date, Map.of());

            for (Map.Entry<String, Integer> entry : itemColumns.entrySet()) {
                BigDecimal quantity = quantities.get(entry.getKey());
                if (quantity == null || quantity.signum() == 0) {
                    continue;
                }

                Cell quantityCell = getOrCreateCell(
                        row,
                        entry.getValue()
                );
                quantityCell.setCellValue(quantity.doubleValue());
            }

            BigDecimal returnQuantity = quantities.get("회수통");
            Integer returnColumn = itemColumns.get("회수통");

            if (returnColumn != null
                    && returnQuantity != null
                    && returnQuantity.signum() != 0) {
                BigDecimal returnAmount = returnContainerAmountByDate
                        .getOrDefault(date, BigDecimal.ZERO);

                applyReturnContainerAmountToFormula(
                        sheet,
                        row,
                        returnColumn,
                        returnAmount,
                        date,
                        returnQuantity,
                        warnings
                );
            }
        }
    }

    private void addMissingItemColumnWarnings(
            String statementName,
            List<SalesItemEntity> items,
            Set<String> availableItems,
            List<GenerationWarning> warnings
    ) {
        for (SalesItemEntity item : items) {
            String normalizedItem = normalizeItemName(item.getItemName());
            if (availableItems.contains(normalizedItem)) {
                continue;
            }

            warnings.add(new GenerationWarning(
                    "품목 열 없음",
                    statementName,
                    item.getSalesOrder().getDeliveryDate(),
                    item.getItemName(),
                    item.getQuantity(),
                    "template.xlsx의 32행에 해당 품목 열이 없어 수량을 입력하지 못했습니다."
            ));
        }
    }

    private void addMissingTemplateWarnings(
            Map<String, List<SalesItemEntity>> itemsByStatementName,
            Set<String> templateStatementNames,
            YearMonth month,
            List<GenerationWarning> warnings
    ) {
        LocalDate normalStart = month.atDay(1);
        LocalDate normalEnd = month.atEndOfMonth();

        for (Map.Entry<String, List<SalesItemEntity>> entry
                : itemsByStatementName.entrySet()) {
            if (templateStatementNames.contains(entry.getKey())) {
                continue;
            }

            for (SalesItemEntity item : entry.getValue()) {
                LocalDate date = item.getSalesOrder().getDeliveryDate();
                if (date.isBefore(normalStart) || date.isAfter(normalEnd)) {
                    continue;
                }

                warnings.add(new GenerationWarning(
                        "거래처 시트 없음",
                        entry.getKey(),
                        date,
                        item.getItemName(),
                        item.getQuantity(),
                        "template.xlsx에 거래처 시트가 없어 명세서를 만들지 못했습니다."
                ));
            }
        }
    }

    private void createWarningSheet(
            XSSFWorkbook workbook,
            List<GenerationWarning> warnings
    ) {
        String sheetName = workbook.getSheet(WARNING_SHEET_NAME) == null
                ? WARNING_SHEET_NAME
                : "생성확인_경고";

        XSSFSheet sheet = workbook.createSheet(sheetName);
        workbook.setSheetOrder(sheetName, 0);

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(
                "아래 판매자료는 템플릿에 맞는 시트 또는 품목 열이 없어 명세서에 반영되지 않았습니다."
        );

        Row headerRow = sheet.createRow(2);
        String[] headers = {
                "구분", "거래처", "날짜", "품목", "수량", "사유"
        };

        for (int column = 0; column < headers.length; column++) {
            Cell cell = headerRow.createCell(column);
            cell.setCellValue(headers[column]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 3;
        for (GenerationWarning warning : warnings) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(warning.type());
            row.createCell(1).setCellValue(warning.statementName());
            row.createCell(2).setCellValue(warning.deliveryDate().toString());
            row.createCell(3).setCellValue(warning.itemName());
            row.createCell(4).setCellValue(warning.quantity().doubleValue());
            row.createCell(5).setCellValue(warning.reason());
        }

        sheet.setColumnWidth(0, 18 * 256);
        sheet.setColumnWidth(1, 26 * 256);
        sheet.setColumnWidth(2, 14 * 256);
        sheet.setColumnWidth(3, 18 * 256);
        sheet.setColumnWidth(4, 12 * 256);
        sheet.setColumnWidth(5, 65 * 256);
        sheet.createFreezePane(0, 3);
    }

    private void writePeriod(
            XSSFSheet sheet,
            String periodText
    ) {
        Row row = getOrCreateRow(sheet, PERIOD_ROW_INDEX);
        Cell cell = getOrCreateCell(row, 0);
        cell.setCellValue(periodText);
    }

    private Map<String, Integer> detectItemColumns(XSSFSheet sheet) {
        Row headerRow = sheet.getRow(HEADER_ROW_INDEX);
        if (headerRow == null) {
            throw new IllegalArgumentException(
                    sheet.getSheetName()
                            + " 시트에서 32행 품목 헤더를 찾을 수 없습니다."
            );
        }

        Map<String, Integer> itemColumns = new LinkedHashMap<>();
        int lastCell = Math.max(headerRow.getLastCellNum(), 0);

        for (int columnIndex = 0;
             columnIndex < lastCell;
             columnIndex++) {
            Cell cell = headerRow.getCell(columnIndex);
            String rawLabel = cell == null
                    ? ""
                    : dataFormatter.formatCellValue(cell).trim();

            String normalizedItem =
                    ItemCatalog.normalizeTemplateLabel(rawLabel);

            if (normalizedItem != null) {
                itemColumns.put(normalizedItem, columnIndex);
            }
        }

        if (itemColumns.isEmpty()) {
            throw new IllegalArgumentException(
                    sheet.getSheetName()
                            + " 시트에서 사용할 수 있는 품목 열을 찾지 못했습니다."
            );
        }

        return itemColumns;
    }

    private void clearDataArea(
            XSSFSheet sheet,
            Map<String, Integer> itemColumns
    ) {
        for (int rowIndex = DATA_START_ROW_INDEX;
             rowIndex <= DATA_END_ROW_INDEX;
             rowIndex++) {
            Row row = getOrCreateRow(sheet, rowIndex);

            getOrCreateCell(row, DATE_COLUMN_INDEX).setBlank();

            for (Integer columnIndex : itemColumns.values()) {
                getOrCreateCell(row, columnIndex).setBlank();
            }
        }
    }

    private Map<LocalDate, BigDecimal> createReturnContainerAmountByDate(
            List<SalesItemEntity> items
    ) {
        Map<LocalDate, BigDecimal> amounts = new LinkedHashMap<>();

        for (SalesItemEntity item : items) {
            if (!"회수통".equals(normalizeItemName(item.getItemName()))) {
                continue;
            }

            BigDecimal lineAmount = item.getLineAmount();
            if (lineAmount == null) {
                continue;
            }

            LocalDate date = item.getSalesOrder().getDeliveryDate();
            amounts.merge(date, lineAmount, BigDecimal::add);
        }

        return amounts;
    }

    /**
     * template.xlsx에 잘못 남아 있는 회수통 수식도 생성 시점에 바로잡습니다.
     *
     * 기존 수식의 F33*3000 같은 회수통 항을 제거한 뒤, DB에 실제 저장된
     * 해당 날짜의 회수통 금액을 상수로 더하거나 뺍니다. 그래서 거래처별
     * 기본단가가 다르거나 주문별 예외 단가가 있어도 실제 매출과 일치합니다.
     */
    private void applyReturnContainerAmountToFormula(
            XSSFSheet sheet,
            Row row,
            int returnColumn,
            BigDecimal returnAmount,
            LocalDate date,
            BigDecimal returnQuantity,
            List<GenerationWarning> warnings
    ) {
        Cell returnCell = getOrCreateCell(row, returnColumn);
        String returnReference =
                returnCell.getAddress().formatAsString();

        List<Cell> changedFormulaCells = new ArrayList<>();
        Cell nearestFormulaCell = null;
        int lastCell = Math.max(row.getLastCellNum(), returnColumn + 1);

        for (int column = returnColumn + 1;
             column < lastCell;
             column++) {
            Cell cell = row.getCell(column);
            if (cell == null || cell.getCellType() != CellType.FORMULA) {
                continue;
            }

            if (nearestFormulaCell == null) {
                nearestFormulaCell = cell;
            }

            String originalFormula = cell.getCellFormula();
            String cleanedFormula = removeReturnContainerTerm(
                    originalFormula,
                    returnReference
            );

            if (!cleanedFormula.equals(originalFormula)) {
                cell.setCellFormula(cleanedFormula);
                changedFormulaCells.add(cell);
            }
        }

        List<Cell> targetCells;
        if (!changedFormulaCells.isEmpty()) {
            targetCells = changedFormulaCells;
        } else if (nearestFormulaCell != null) {
            targetCells = List.of(nearestFormulaCell);
        } else {
            warnings.add(new GenerationWarning(
                    "회수통 합계 수식 없음",
                    sheet.getSheetName(),
                    date,
                    "회수통",
                    returnQuantity,
                    "회수통 수량은 있으나 오른쪽에서 합계 수식을 찾지 못해 "
                            + "회수통 금액을 명세서 합계에 반영하지 못했습니다."
            ));
            return;
        }

        for (Cell targetCell : targetCells) {
            targetCell.setCellFormula(appendAmountToFormula(
                    targetCell.getCellFormula(),
                    returnAmount
            ));
        }
    }

    private String removeReturnContainerTerm(
            String formula,
            String returnReference
    ) {
        String reference = Pattern.quote(returnReference);

        String cleaned = formula.replaceAll(
                "(?i)([+-])\\s*" + reference
                        + "\\s*\\*\\s*-?\\d+(?:\\.\\d+)?",
                ""
        );

        return cleaned;
    }

    private String appendAmountToFormula(
            String formula,
            BigDecimal amount
    ) {
        if (amount == null || amount.signum() == 0) {
            return formula;
        }

        BigDecimal normalized = amount.stripTrailingZeros();
        String sign = normalized.signum() < 0 ? "-" : "+";

        return formula
                + sign
                + normalized.abs().toPlainString();
    }

    private Map<LocalDate, Map<String, BigDecimal>> createQuantityPivot(
            List<SalesItemEntity> items
    ) {
        Map<LocalDate, Map<String, BigDecimal>> pivot =
                new LinkedHashMap<>();

        for (SalesItemEntity item : items) {
            LocalDate date = item.getSalesOrder().getDeliveryDate();
            String normalizedItem = normalizeItemName(item.getItemName());

            pivot.computeIfAbsent(
                    date,
                    ignored -> new LinkedHashMap<>()
            ).merge(
                    normalizedItem,
                    item.getQuantity(),
                    BigDecimal::add
            );
        }

        return pivot;
    }

    private String normalizeItemName(String rawItemName) {
        String normalized = ItemCatalog.normalizeTemplateLabel(rawItemName);
        if (normalized != null) {
            return normalized;
        }
        return rawItemName == null ? "" : rawItemName.trim();
    }

    private boolean isWithinPeriod(
            SalesItemEntity item,
            LocalDate startDate,
            LocalDate endDate
    ) {
        LocalDate deliveryDate =
                item.getSalesOrder().getDeliveryDate();

        return !deliveryDate.isBefore(startDate)
                && !deliveryDate.isAfter(endDate);
    }

    private void recalculateFormulas(XSSFWorkbook workbook) {
        workbook.setForceFormulaRecalculation(true);

        try {
            FormulaEvaluator evaluator =
                    workbook.getCreationHelper().createFormulaEvaluator();
            evaluator.evaluateAll();
        } catch (RuntimeException ignored) {
            // Excel을 열 때 전체 수식을 다시 계산하도록 설정되어 있으므로
            // POI가 일부 수식을 평가하지 못해도 파일 생성은 계속합니다.
        }
    }

    private Row getOrCreateRow(
            XSSFSheet sheet,
            int rowIndex
    ) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? sheet.createRow(rowIndex) : row;
    }

    private Cell getOrCreateCell(
            Row row,
            int columnIndex
    ) {
        Cell cell = row.getCell(columnIndex);
        return cell == null
                ? row.createCell(columnIndex, CellType.BLANK)
                : cell;
    }

    private void validateTemplate(MultipartFile templateFile) {
        if (templateFile == null || templateFile.isEmpty()) {
            throw new IllegalArgumentException(
                    "template.xlsx 파일을 선택해주세요."
            );
        }

        String originalFilename = templateFile.getOriginalFilename();
        if (originalFilename == null
                || !originalFilename.toLowerCase(Locale.ROOT)
                .endsWith(".xlsx")) {
            throw new IllegalArgumentException(
                    ".xlsx 형식의 템플릿만 사용할 수 있습니다."
            );
        }
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private record StatementPeriod(
            LocalDate startDate,
            LocalDate endDate,
            String periodText
    ) {
    }

    private record GenerationWarning(
            String type,
            String statementName,
            LocalDate deliveryDate,
            String itemName,
            BigDecimal quantity,
            String reason
    ) {
    }
}
