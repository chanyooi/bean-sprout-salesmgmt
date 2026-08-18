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
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
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

@Service
public class StatementWorkbookService {

    private static final String SUNSAN_STATEMENT_NAME = "선산식자재마트";
    private static final String WARNING_SHEET_NAME = "생성확인";
    private static final String DEFAULT_TEMPLATE_PATH = "template.xlsx";

    private static final int DATE_COLUMN_INDEX = 0;
    private static final int MAX_HEADER_SCAN_ROW = 50;

    private final SalesItemRepository salesItemRepository;
    private final DataFormatter dataFormatter =
            new DataFormatter(Locale.KOREA);

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
        LocalDate normalStart = month.atDay(1);
        LocalDate normalEnd = month.atEndOfMonth();

        LocalDate sunsanStart =
                month.minusMonths(1).atDay(26);

        List<SalesItemEntity> allItems =
                salesItemRepository.findForMonthlyReport(
                        sunsanStart,
                        normalEnd
                );

        Map<String, List<SalesItemEntity>> itemsByStatementName =
                groupByStatementName(allItems);

        try (
                InputStream inputStream = openTemplate(templateFile);
                XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
                ByteArrayOutputStream outputStream =
                        new ByteArrayOutputStream()
        ) {
            List<Integer> sheetIndexesToRemove =
                    new ArrayList<>();

            List<GenerationWarning> warnings =
                    new ArrayList<>();

            Set<String> templateStatementNames =
                    new HashSet<>();

            int sheetWithSalesCount = 0;
            int originalSheetCount =
                    workbook.getNumberOfSheets();

            for (
                    int sheetIndex = 0;
                    sheetIndex < originalSheetCount;
                    sheetIndex++
            ) {
                XSSFSheet sheet =
                        workbook.getSheetAt(sheetIndex);

                String statementName =
                        normalizeName(sheet.getSheetName());

                templateStatementNames.add(statementName);

                TemplateLayout layout =
                        detectLayout(sheet);

                StatementPeriod period =
                        statementPeriod(
                                statementName,
                                month,
                                normalStart,
                                normalEnd,
                                sunsanStart
                        );

                List<SalesItemEntity> statementItems =
                        itemsByStatementName
                                .getOrDefault(
                                        statementName,
                                        List.of()
                                )
                                .stream()
                                .filter(
                                        item ->
                                                isWithinPeriod(
                                                        item,
                                                        period.startDate(),
                                                        period.endDate()
                                                )
                                )
                                .toList();

                if (
                        statementItems.isEmpty()
                        && !includeEmptySheets
                ) {
                    sheetIndexesToRemove.add(sheetIndex);
                    continue;
                }

                if (!statementItems.isEmpty()) {
                    sheetWithSalesCount++;
                }

                fillSheet(
                        sheet,
                        layout,
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

            for (
                    int index = sheetIndexesToRemove.size() - 1;
                    index >= 0;
                    index--
            ) {
                workbook.removeSheetAt(
                        sheetIndexesToRemove.get(index)
                );
            }

            int generatedStatementSheetCount =
                    workbook.getNumberOfSheets();

            if (generatedStatementSheetCount == 0) {
                throw new IllegalArgumentException(
                        month
                                + "에 생성할 명세서가 없습니다. "
                                + "빈 명세서 포함을 선택하거나 "
                                + "판매자료를 확인해주세요."
                );
            }

            if (!warnings.isEmpty()) {
                createWarningSheet(
                        workbook,
                        warnings
                );
            }

            prepareFormulaRecalculation(workbook);

            workbook.write(outputStream);

            String filename =
                    month.getYear()
                            + "년_"
                            + String.format(
                                    "%02d",
                                    month.getMonthValue()
                            )
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
                    "template.xlsx를 읽거나 "
                            + "명세서 파일을 만드는 중 "
                            + "오류가 발생했습니다.",
                    exception
            );
        }
    }

    private TemplateLayout detectLayout(
            XSSFSheet sheet
    ) {
        int lastRowToScan =
                Math.min(
                        sheet.getLastRowNum(),
                        MAX_HEADER_SCAN_ROW
                );

        for (
                int rowIndex = 0;
                rowIndex <= lastRowToScan;
                rowIndex++
        ) {
            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            String firstCell =
                    formatted(
                            row.getCell(
                                    DATE_COLUMN_INDEX
                            )
                    );

            if (!"날짜".equals(firstCell)) {
                continue;
            }

            Map<String, Integer> itemColumns =
                    detectItemColumns(row);

            if (itemColumns.isEmpty()) {
                continue;
            }

            int sumRowIndex =
                    findSumRowIndex(
                            sheet,
                            rowIndex + 1
                    );

            int dataStartRowIndex =
                    rowIndex + 1;

            int dataEndRowIndex =
                    sumRowIndex > dataStartRowIndex
                            ? sumRowIndex - 1
                            : dataStartRowIndex + 30;

            if (
                    dataEndRowIndex
                            - dataStartRowIndex
                            + 1
                            > 31
            ) {
                dataEndRowIndex =
                        dataStartRowIndex + 30;
            }

            int periodRowIndex =
                    rowIndex <= 30
                            ? 2
                            : 6;

            return new TemplateLayout(
                    rowIndex,
                    dataStartRowIndex,
                    dataEndRowIndex,
                    sumRowIndex,
                    periodRowIndex,
                    itemColumns
            );
        }

        throw new IllegalArgumentException(
                sheet.getSheetName()
                        + " 시트에서 거래명세서의 "
                        + "'날짜' 헤더 행을 찾지 못했습니다."
        );
    }

    private int findSumRowIndex(
            XSSFSheet sheet,
            int startRowIndex
    ) {
        int limit =
                Math.min(
                        sheet.getLastRowNum(),
                        startRowIndex + 40
                );

        for (
                int rowIndex = startRowIndex;
                rowIndex <= limit;
                rowIndex++
        ) {
            Row row =
                    sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            if (
                    "합계".equals(
                            formatted(
                                    row.getCell(
                                            DATE_COLUMN_INDEX
                                    )
                            )
                    )
            ) {
                return rowIndex;
            }
        }

        return startRowIndex + 31;
    }

    private Map<String, Integer> detectItemColumns(
            Row headerRow
    ) {
        Map<String, Integer> itemColumns =
                new LinkedHashMap<>();

        int lastCell =
                Math.max(
                        headerRow.getLastCellNum(),
                        0
                );

        for (
                int columnIndex = 0;
                columnIndex < lastCell;
                columnIndex++
        ) {
            Cell cell =
                    headerRow.getCell(columnIndex);

            String rawLabel =
                    formatted(cell);

            String normalizedItem =
                    normalizeTemplateItem(rawLabel);

            if (normalizedItem != null) {
                itemColumns.putIfAbsent(
                        normalizedItem,
                        columnIndex
                );
            }
        }

        return itemColumns;
    }

    private String normalizeTemplateItem(
            String rawLabel
    ) {
        if (
                rawLabel == null
                || rawLabel.isBlank()
        ) {
            return null;
        }

        String label =
                rawLabel.trim();

        if ("일소".equals(label)) {
            return "회수통";
        }

        if (
                "일반(소)콩나물".equals(label)
                || "일반소콩나물".equals(label)
                || "일반소".equals(label)
                || "일반".equals(label)
        ) {
            return "일반콩나물";
        }

        if ("두절".equals(label)) {
            return "두절kg";
        }

        if ("3.5일반".equals(label)) {
            return "3.5kg일반";
        }

        if ("3.5곱슬".equals(label)) {
            return "3.5kg곱슬";
        }

        if (
                "날짜".equals(label)
                || "합계".equals(label)
        ) {
            return null;
        }

        String catalogValue =
                ItemCatalog.normalizeTemplateLabel(
                        label
                );

        if (catalogValue != null) {
            return catalogValue;
        }

        if (
                "손두부".equals(label)
                || "두부판".equals(label)
                || "회수통".equals(label)
                || "일반콩나물".equals(label)
                || "곱슬콩나물".equals(label)
                || "두절kg".equals(label)
                || "3.5kg일반".equals(label)
                || "3.5kg곱슬".equals(label)
                || "숙주".equals(label)
                || "소립".equals(label)
        ) {
            return label;
        }

        return null;
    }

    private void fillSheet(
            XSSFSheet sheet,
            TemplateLayout layout,
            StatementPeriod period,
            List<SalesItemEntity> items,
            List<GenerationWarning> warnings
    ) {
        writePeriod(
                sheet,
                layout,
                period.periodText()
        );

        clearDataArea(
                sheet,
                layout
        );

        addMissingItemColumnWarnings(
                sheet.getSheetName(),
                items,
                layout.itemColumns().keySet(),
                layout.headerRowIndex(),
                warnings
        );

        Map<LocalDate, Map<String, BigDecimal>> quantityPivot =
                createQuantityPivot(items);

        List<LocalDate> dates =
                period.startDate()
                        .datesUntil(
                                period.endDate().plusDays(1)
                        )
                        .toList();

        int availableRows =
                layout.dataEndRowIndex()
                        - layout.dataStartRowIndex()
                        + 1;

        if (dates.size() > availableRows) {
            throw new IllegalArgumentException(
                    sheet.getSheetName()
                            + " 시트의 날짜 입력 행이 부족합니다. 필요 "
                            + dates.size()
                            + "행 / 템플릿 "
                            + availableRows
                            + "행"
            );
        }

        for (int offset = 0; offset < dates.size(); offset++) {
            LocalDate date = dates.get(offset);
            int rowIndex = layout.dataStartRowIndex() + offset;
            Row row = getOrCreateRow(sheet, rowIndex);

            Cell dateCell = getOrCreateCell(row, DATE_COLUMN_INDEX);
            dateCell.setCellValue(Date.valueOf(date));

            Map<String, BigDecimal> quantities =
                    quantityPivot.getOrDefault(date, Map.of());

            for (Map.Entry<String, Integer> entry
                    : layout.itemColumns().entrySet()) {
                BigDecimal quantity = quantities.get(entry.getKey());

                if (quantity == null || quantity.signum() == 0) {
                    continue;
                }

                Cell quantityCell =
                        getOrCreateCell(row, entry.getValue());
                quantityCell.setCellValue(quantity.doubleValue());
            }
        }
    }

    private void writePeriod(
            XSSFSheet sheet,
            TemplateLayout layout,
            String periodText
    ) {
        Row row =
                getOrCreateRow(
                        sheet,
                        layout.periodRowIndex()
                );

        Cell cell =
                getOrCreateCell(
                        row,
                        DATE_COLUMN_INDEX
                );

        cell.setCellValue(periodText);
    }

    private void clearDataArea(
            XSSFSheet sheet,
            TemplateLayout layout
    ) {
        for (
                int rowIndex = layout.dataStartRowIndex();
                rowIndex <= layout.dataEndRowIndex();
                rowIndex++
        ) {
            Row row = getOrCreateRow(sheet, rowIndex);

            getOrCreateCell(row, DATE_COLUMN_INDEX).setBlank();

            for (Integer columnIndex
                    : layout.itemColumns().values()) {
                getOrCreateCell(row, columnIndex).setBlank();
            }
        }
    }

    private void addMissingItemColumnWarnings(
            String statementName,
            List<SalesItemEntity> items,
            Set<String> availableItems,
            int headerRowIndex,
            List<GenerationWarning> warnings
    ) {
        for (SalesItemEntity item : items) {
            String normalizedItem =
                    normalizeItemName(item.getItemName());

            if (availableItems.contains(normalizedItem)) {
                continue;
            }

            warnings.add(
                    new GenerationWarning(
                            "품목 열 없음",
                            statementName,
                            item.getSalesOrder().getDeliveryDate(),
                            item.getItemName(),
                            item.getQuantity(),
                            "template.xlsx의 "
                                    + (headerRowIndex + 1)
                                    + "행에 해당 품목 열이 없어 수량을 입력하지 못했습니다."
                    )
            );
        }
    }

    private Map<String, List<SalesItemEntity>> groupByStatementName(
            List<SalesItemEntity> items
    ) {
        Map<String, List<SalesItemEntity>> grouped = new HashMap<>();

        for (SalesItemEntity item : items) {
            String statementName =
                    normalizeName(
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
                month.getYear()
                        + "년 "
                        + month.getMonthValue()
                        + "월"
        );
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
                LocalDate date =
                        item.getSalesOrder().getDeliveryDate();

                if (date.isBefore(normalStart)
                        || date.isAfter(normalEnd)) {
                    continue;
                }

                warnings.add(
                        new GenerationWarning(
                                "거래처 시트 없음",
                                entry.getKey(),
                                date,
                                item.getItemName(),
                                item.getQuantity(),
                                "template.xlsx에 거래처 시트가 없어 명세서를 만들지 못했습니다."
                        )
                );
            }
        }
    }

    private void createWarningSheet(
            XSSFWorkbook workbook,
            List<GenerationWarning> warnings
    ) {
        String sheetName =
                workbook.getSheet(WARNING_SHEET_NAME) == null
                        ? WARNING_SHEET_NAME
                        : "생성확인_경고";

        XSSFSheet sheet = workbook.createSheet(sheetName);
        workbook.setSheetOrder(sheetName, 0);

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(
                IndexedColors.LIGHT_YELLOW.getIndex()
        );
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(
                "아래 판매자료는 템플릿에 맞는 시트 또는 품목 열이 없어 명세서에 반영되지 않았습니다."
        );

        Row headerRow = sheet.createRow(2);
        String[] headers = {
                "구분",
                "거래처",
                "날짜",
                "품목",
                "수량",
                "사유"
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

    private Map<LocalDate, Map<String, BigDecimal>> createQuantityPivot(
            List<SalesItemEntity> items
    ) {
        Map<LocalDate, Map<String, BigDecimal>> pivot =
                new LinkedHashMap<>();

        for (SalesItemEntity item : items) {
            LocalDate date =
                    item.getSalesOrder().getDeliveryDate();

            String normalizedItem =
                    normalizeItemName(item.getItemName());

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

    private String normalizeItemName(
            String rawItemName
    ) {
        String normalized =
                ItemCatalog.normalizeTemplateLabel(rawItemName);

        if (normalized != null) {
            return normalized;
        }

        return rawItemName == null
                ? ""
                : rawItemName.trim();
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

    private void prepareFormulaRecalculation(
            XSSFWorkbook workbook
    ) {
        // Railway에서 모든 수식을 서버 CPU로 계산하면 POI가 큰 부하를 만들 수 있다.
        // 계산은 Excel이 파일을 열 때 하도록 플래그만 설정한다.
        workbook.setForceFormulaRecalculation(true);
    }

    private String formatted(
            Cell cell
    ) {
        if (cell == null) {
            return "";
        }

        return dataFormatter
                .formatCellValue(cell)
                .trim();
    }

    private Row getOrCreateRow(
            XSSFSheet sheet,
            int rowIndex
    ) {
        Row row = sheet.getRow(rowIndex);

        return row == null
                ? sheet.createRow(rowIndex)
                : row;
    }

    private Cell getOrCreateCell(
            Row row,
            int columnIndex
    ) {
        Cell cell = row.getCell(columnIndex);

        return cell == null
                ? row.createCell(
                        columnIndex,
                        CellType.BLANK
                )
                : cell;
    }

    private InputStream openTemplate(
            MultipartFile templateFile
    ) throws IOException {
        if (templateFile != null
                && !templateFile.isEmpty()) {
            String originalFilename =
                    templateFile.getOriginalFilename();

            if (originalFilename == null
                    || !originalFilename
                            .toLowerCase(Locale.ROOT)
                            .endsWith(".xlsx")) {
                throw new IllegalArgumentException(
                        ".xlsx 형식의 템플릿만 사용할 수 있습니다."
                );
            }

            return templateFile.getInputStream();
        }

        ClassPathResource resource =
                new ClassPathResource(DEFAULT_TEMPLATE_PATH);

        if (!resource.exists()) {
            throw new IllegalArgumentException(
                    "기본 template.xlsx 파일을 찾을 수 없습니다. "
                            + "src/main/resources/template.xlsx를 확인해주세요."
            );
        }

        return resource.getInputStream();
    }

    private String normalizeName(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    private record TemplateLayout(
            int headerRowIndex,
            int dataStartRowIndex,
            int dataEndRowIndex,
            int sumRowIndex,
            int periodRowIndex,
            Map<String, Integer> itemColumns
    ) {
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
