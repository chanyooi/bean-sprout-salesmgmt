package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.DeliveryRecord;
import com.example.salesmgmt.domain.ExcelImportResult;
import com.example.salesmgmt.domain.ImportIssue;
import com.example.salesmgmt.domain.OrderSnapshot;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ExcelImportService {

    private static final Pattern DATE_SHEET_PATTERN = Pattern.compile("\\d{8}");
    private static final DateTimeFormatter SHEET_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.KOREA);

    private static final List<String> ITEM_HEADERS = List.of(
            "두절kg",
            "일반콩나물",
            "소립",
            "곱슬콩나물",
            "3.5kg일반",
            "3.5kg곱슬",
            "숙주",
            "회수통",
            "손두부",
            "두부판"
    );

    private static final List<String> REQUIRED_HEADERS = List.of(
            "주문번호",
            "날짜",
            "거래처"
    );

    private final VendorRuleService vendorRuleService;

    public ExcelImportService(VendorRuleService vendorRuleService) {
        this.vendorRuleService = vendorRuleService;
    }

    public ExcelImportResult importInputData(MultipartFile file) {
        validateFile(file);

        List<DeliveryRecord> records = new ArrayList<>();
        List<OrderSnapshot> orderSnapshots = new ArrayList<>();
        List<ImportIssue> issues = new ArrayList<>();
        Set<String> warnedVendorsWithoutTemplate = new LinkedHashSet<>();

        int processedSheetCount = 0;
        int salesRowCount = 0;

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            for (Sheet sheet : workbook) {
                String sheetName = sheet.getSheetName();

                if (!DATE_SHEET_PATTERN.matcher(sheetName).matches()) {
                    issues.add(warning(
                            sheetName,
                            "날짜 형식(yyyyMMdd)의 시트가 아니어서 건너뛰었습니다."
                    ));
                    continue;
                }

                processedSheetCount++;

                Row headerRow = findHeaderRow(sheet, formatter, evaluator);
                if (headerRow == null) {
                    issues.add(error(sheetName, "주문번호·날짜·거래처 머리글을 찾지 못했습니다."));
                    continue;
                }

                Map<String, Integer> headerIndexes = buildHeaderIndexes(
                        headerRow,
                        formatter,
                        evaluator
                );

                List<String> missingHeaders = REQUIRED_HEADERS.stream()
                        .filter(header -> !headerIndexes.containsKey(header))
                        .toList();

                if (!missingHeaders.isEmpty()) {
                    issues.add(error(
                            sheetName,
                            "필수 열이 없습니다: " + String.join(", ", missingHeaders)
                    ));
                    continue;
                }

                int firstDataRow = headerRow.getRowNum() + 2;

                for (int rowIndex = firstDataRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        continue;
                    }

                    String vendor = readText(
                            row.getCell(headerIndexes.get("거래처")),
                            formatter,
                            evaluator
                    );

                    if (vendor.isBlank()) {
                        continue;
                    }

                    LocalDate deliveryDate = readDate(
                            row.getCell(headerIndexes.get("날짜")),
                            evaluator,
                            sheetName,
                            rowIndex + 1,
                            issues
                    );

                    if (deliveryDate == null) {
                        continue;
                    }

                    String orderNumber = readText(
                            row.getCell(headerIndexes.get("주문번호")),
                            formatter,
                            evaluator
                    );

                    if (orderNumber.isBlank()) {
                        int sequence = row.getRowNum() - headerRow.getRowNum() - 1;
                        orderNumber = "%s-%03d".formatted(
                                deliveryDate.format(SHEET_DATE_FORMAT),
                                sequence
                        );
                    }

                    BigDecimal returnContainerUnitPrice = readOptionalNumber(
                            row,
                            headerIndexes,
                            "회수통단가",
                            evaluator,
                            sheetName,
                            rowIndex + 1,
                            issues
                    );

                    String deliveryMethod = readOptionalText(
                            row,
                            headerIndexes,
                            "전달방식",
                            formatter,
                            evaluator
                    );

                    String note = readOptionalText(
                            row,
                            headerIndexes,
                            "비고",
                            formatter,
                            evaluator
                    );

                    orderSnapshots.add(new OrderSnapshot(
                            orderNumber,
                            deliveryDate,
                            vendor,
                            vendorRuleService.statementVendorName(vendor),
                            returnContainerUnitPrice,
                            deliveryMethod,
                            note,
                            sheetName,
                            rowIndex + 1
                    ));

                    int recordCountBeforeRow = records.size();

                    for (String item : ITEM_HEADERS) {
                        Integer cellIndex = headerIndexes.get(item);
                        if (cellIndex == null) {
                            continue;
                        }

                        Cell quantityCell = row.getCell(cellIndex);
                        BigDecimal quantity = readQuantity(
                                quantityCell,
                                evaluator,
                                sheetName,
                                rowIndex + 1,
                                item,
                                issues
                        );

                        if (quantity == null || quantity.signum() == 0) {
                            continue;
                        }

                        if (quantity.signum() < 0) {
                            issues.add(error(
                                    "%s!%s".formatted(
                                            sheetName,
                                            quantityCell.getAddress().formatAsString()
                                    ),
                                    item + " 수량은 음수일 수 없습니다."
                            ));
                            continue;
                        }

                        records.add(new DeliveryRecord(
                                orderNumber,
                                deliveryDate,
                                vendor,
                                vendorRuleService.statementVendorName(vendor),
                                item,
                                quantity.stripTrailingZeros(),
                                returnContainerUnitPrice,
                                deliveryMethod,
                                note,
                                sheetName,
                                rowIndex + 1
                        ));
                    }

                    if (records.size() > recordCountBeforeRow) {
                        salesRowCount++;

                        if (!vendorRuleService.hasStatementTemplate(vendor)
                                && warnedVendorsWithoutTemplate.add(vendor)) {
                            issues.add(warning(
                                    sheetName + "!" + (rowIndex + 1),
                                    vendor + "은(는) 현재 명세서 템플릿이 없습니다. "
                                            + "판매 데이터는 정상적으로 가져왔습니다."
                            ));
                        }
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "엑셀 파일을 읽는 중 오류가 발생했습니다. "
                            + "파일이 손상되지 않았는지 확인해주세요.",
                    exception
            );
        }

        return new ExcelImportResult(
                processedSheetCount,
                salesRowCount,
                List.copyOf(records),
                List.copyOf(orderSnapshots),
                List.copyOf(issues)
        );
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 input_data.xlsx 파일을 선택해주세요.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException(".xlsx 형식의 파일만 업로드할 수 있습니다.");
        }
    }

    private Row findHeaderRow(
            Sheet sheet,
            DataFormatter formatter,
            FormulaEvaluator evaluator
    ) {
        int lastCandidateRow = Math.min(sheet.getLastRowNum(), 10);

        for (int rowIndex = sheet.getFirstRowNum();
             rowIndex <= lastCandidateRow;
             rowIndex++) {

            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            Set<String> values = new LinkedHashSet<>();
            for (Cell cell : row) {
                String value = readText(cell, formatter, evaluator);
                if (!value.isBlank()) {
                    values.add(value);
                }
            }

            if (values.containsAll(REQUIRED_HEADERS)) {
                return row;
            }
        }

        return null;
    }

    private Map<String, Integer> buildHeaderIndexes(
            Row headerRow,
            DataFormatter formatter,
            FormulaEvaluator evaluator
    ) {
        Map<String, Integer> indexes = new LinkedHashMap<>();

        for (Cell cell : headerRow) {
            String header = readText(cell, formatter, evaluator);
            if (!header.isBlank()) {
                indexes.putIfAbsent(header, cell.getColumnIndex());
            }
        }

        return indexes;
    }

    private String readOptionalText(
            Row row,
            Map<String, Integer> headerIndexes,
            String header,
            DataFormatter formatter,
            FormulaEvaluator evaluator
    ) {
        Integer cellIndex = headerIndexes.get(header);
        if (cellIndex == null) {
            return "";
        }

        return readText(row.getCell(cellIndex), formatter, evaluator);
    }

    private BigDecimal readOptionalNumber(
            Row row,
            Map<String, Integer> headerIndexes,
            String header,
            FormulaEvaluator evaluator,
            String sheetName,
            int excelRowNumber,
            List<ImportIssue> issues
    ) {
        Integer cellIndex = headerIndexes.get(header);
        if (cellIndex == null) {
            return null;
        }

        Cell cell = row.getCell(cellIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }

        try {
            return readNumber(cell, evaluator);
        } catch (NumberFormatException exception) {
            issues.add(error(
                    "%s!%s".formatted(sheetName, cell.getAddress().formatAsString()),
                    "%s 값이 숫자가 아닙니다. 입력 행: %d".formatted(header, excelRowNumber)
            ));
            return null;
        }
    }

    private BigDecimal readQuantity(
            Cell cell,
            FormulaEvaluator evaluator,
            String sheetName,
            int excelRowNumber,
            String item,
            List<ImportIssue> issues
    ) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }

        try {
            return readNumber(cell, evaluator);
        } catch (NumberFormatException exception) {
            issues.add(error(
                    "%s!%s".formatted(sheetName, cell.getAddress().formatAsString()),
                    "%s 수량이 숫자가 아닙니다. 입력 행: %d".formatted(item, excelRowNumber)
            ));
            return null;
        }
    }

    private BigDecimal readNumber(Cell cell, FormulaEvaluator evaluator) {
        CellType effectiveType = cell.getCellType();

        if (effectiveType == CellType.FORMULA) {
            var evaluated = evaluator.evaluate(cell);
            if (evaluated == null) {
                throw new NumberFormatException("수식 계산 결과 없음");
            }

            if (evaluated.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(evaluated.getNumberValue());
            }

            if (evaluated.getCellType() == CellType.STRING) {
                return parseNumberText(evaluated.getStringValue());
            }

            if (evaluated.getCellType() == CellType.BLANK) {
                return null;
            }

            throw new NumberFormatException("숫자 수식이 아님");
        }

        if (effectiveType == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }

        if (effectiveType == CellType.STRING) {
            String value = cell.getStringCellValue();
            if (value == null || value.isBlank()) {
                return null;
            }
            return parseNumberText(value);
        }

        throw new NumberFormatException("숫자 셀이 아님");
    }

    private BigDecimal parseNumberText(String value) {
        String cleaned = value == null
                ? ""
                : value.replace(",", "").trim();

        if (cleaned.isBlank()) {
            return null;
        }

        return new BigDecimal(cleaned);
    }

    private LocalDate readDate(
            Cell cell,
            FormulaEvaluator evaluator,
            String sheetName,
            int excelRowNumber,
            List<ImportIssue> issues
    ) {
        if (cell != null && cell.getCellType() != CellType.BLANK) {
            try {
                if (cell.getCellType() == CellType.NUMERIC) {
                    return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate();
                }

                if (cell.getCellType() == CellType.FORMULA) {
                    var evaluated = evaluator.evaluate(cell);
                    if (evaluated != null && evaluated.getCellType() == CellType.NUMERIC) {
                        return DateUtil.getLocalDateTime(
                                evaluated.getNumberValue()
                        ).toLocalDate();
                    }

                    if (evaluated != null && evaluated.getCellType() == CellType.STRING) {
                        return parseDateText(evaluated.getStringValue());
                    }
                }

                if (cell.getCellType() == CellType.STRING) {
                    return parseDateText(cell.getStringCellValue());
                }
            } catch (RuntimeException exception) {
                issues.add(error(
                        "%s!%s".formatted(
                                sheetName,
                                cell.getAddress().formatAsString()
                        ),
                        "날짜를 읽을 수 없습니다. 입력 행: " + excelRowNumber
                ));
                return null;
            }
        }

        try {
            return LocalDate.parse(sheetName, SHEET_DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            issues.add(error(
                    sheetName + "!" + excelRowNumber,
                    "날짜 셀이 비어 있고 시트명에서도 날짜를 확인할 수 없습니다."
            ));
            return null;
        }
    }

    private LocalDate parseDateText(String value) {
        String trimmed = value == null ? "" : value.trim();

        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("yyyy.MM.dd"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                SHEET_DATE_FORMAT
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // 다음 형식으로 시도합니다.
            }
        }

        throw new DateTimeParseException("지원하지 않는 날짜 형식", trimmed, 0);
    }

    private String readText(
            Cell cell,
            DataFormatter formatter,
            FormulaEvaluator evaluator
    ) {
        if (cell == null) {
            return "";
        }

        return formatter.formatCellValue(cell, evaluator).trim();
    }

    private ImportIssue warning(String location, String message) {
        return new ImportIssue(
                ImportIssue.IssueLevel.WARNING,
                location,
                message
        );
    }

    private ImportIssue error(String location, String message) {
        return new ImportIssue(
                ImportIssue.IssueLevel.ERROR,
                location,
                message
        );
    }
}
