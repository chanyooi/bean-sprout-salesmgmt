package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.ItemCatalog;
import com.example.salesmgmt.domain.StatementWorkbookResult;
import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StatementFinalBillingPatchService {

    private static final String SUNSAN_STATEMENT_NAME = "선산식자재마트";

    private final SalesItemRepository salesItemRepository;

    public StatementFinalBillingPatchService(
            SalesItemRepository salesItemRepository
    ) {
        this.salesItemRepository = salesItemRepository;
    }

    /**
     * 월간 명세서의 '최종 청구금액'을 Excel 수식 재계산에 맡기지 않고
     * DB에 저장된 실제 lineAmount 합계로 확정해서 기록합니다.
     */
    @Transactional(readOnly = true)
    public StatementWorkbookResult patchMonthly(
            StatementWorkbookResult result,
            YearMonth month
    ) {
        if (result == null || result.fileBytes() == null || month == null) {
            return result;
        }

        LocalDate queryStart = month.minusMonths(1).atDay(26);
        LocalDate queryEnd = month.atEndOfMonth();
        List<SalesItemEntity> allItems = salesItemRepository.findForMonthlyReport(
                queryStart,
                queryEnd
        );

        Map<String, List<SalesItemEntity>> byStatementName = new LinkedHashMap<>();
        for (SalesItemEntity item : allItems) {
            String statementName = normalize(
                    item.getSalesOrder().getVendor().getStatementName()
            );
            byStatementName.computeIfAbsent(
                    statementName,
                    ignored -> new ArrayList<>()
            ).add(item);
        }

        byte[] patched = patchWorkbook(
                result.fileBytes(),
                sheetName -> billingItems(
                        byStatementName.getOrDefault(normalize(sheetName), List.of()),
                        periodFor(normalize(sheetName), month)
                )
        );

        return copyWithBytes(result, patched);
    }

    /** 거래처 상세에서 한 거래처만 받는 명세서도 같은 기준으로 맞춥니다. */
    @Transactional(readOnly = true)
    public StatementWorkbookResult patchVendor(
            StatementWorkbookResult result,
            Long vendorId,
            YearMonth month
    ) {
        if (result == null
                || result.fileBytes() == null
                || vendorId == null
                || month == null) {
            return result;
        }

        String statementName = "";
        List<SalesItemEntity> broadItems = salesItemRepository.findForVendorPeriod(
                vendorId,
                month.minusMonths(1).atDay(26),
                month.atEndOfMonth()
        );
        if (!broadItems.isEmpty()) {
            statementName = normalize(
                    broadItems.getFirst()
                            .getSalesOrder()
                            .getVendor()
                            .getStatementName()
            );
        }

        StatementPeriod period = periodFor(statementName, month);
        List<SalesItemEntity> periodItems = broadItems.stream()
                .filter(item -> within(
                        item.getSalesOrder().getDeliveryDate(),
                        period.start(),
                        period.end()
                ))
                .toList();
        byte[] patched = patchWorkbook(result.fileBytes(), ignored -> periodItems);
        return copyWithBytes(result, patched);
    }

    private byte[] patchWorkbook(
            byte[] source,
            BillingItemsResolver resolver
    ) {
        try (
                XSSFWorkbook workbook = new XSSFWorkbook(
                        new ByteArrayInputStream(source)
                );
                ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {
            for (int sheetIndex = 0;
                 sheetIndex < workbook.getNumberOfSheets();
                 sheetIndex++) {
                XSSFSheet sheet = workbook.getSheetAt(sheetIndex);
                List<SalesItemEntity> items = resolver.resolve(sheet.getSheetName());
                writeItemSummary(sheet, items);
                writeFinalBillingAmount(sheet, sumLineAmounts(items));
            }

            // 최종 청구금액 자체는 숫자로 확정하고, 나머지 템플릿 수식은
            // 필요하면 Excel에서 다시 계산하도록 유지합니다.
            workbook.setForceFormulaRecalculation(true);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "명세서 최종 청구금액을 확정하는 중 오류가 발생했습니다.",
                    exception
            );
        }
    }

    private List<SalesItemEntity> billingItems(
            List<SalesItemEntity> items,
            StatementPeriod period
    ) {
        return items.stream()
                .filter(item -> within(
                        item.getSalesOrder().getDeliveryDate(),
                        period.start(),
                        period.end()
                ))
                .toList();
    }

    private BigDecimal sumLineAmounts(List<SalesItemEntity> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (SalesItemEntity item : items) {
            total = total.add(
                    ReceivableBillingAdjustmentService.signedVendorLedgerAmount(item)
            );
        }
        return total;
    }

    /**
     * 상단 품목표도 거래처 달력과 같은 주문별 적용단가를 반영합니다.
     * 기간 중 단가가 여러 번 바뀌었으면 월간 실제 금액 / 수량으로 평균단가를 표시합니다.
     */
    private void writeItemSummary(XSSFSheet sheet, List<SalesItemEntity> items) {
        Map<String, BigDecimal> quantities = new LinkedHashMap<>();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        for (SalesItemEntity item : items) {
            String name = ItemCatalog.normalizeTemplateLabel(item.getItemName());
            if (name == null) {
                name = normalize(item.getItemName());
            }
            quantities.merge(name, item.getQuantity(), BigDecimal::add);
            amounts.merge(
                    name,
                    ReceivableBillingAdjustmentService.signedVendorLedgerAmount(item),
                    BigDecimal::add
            );
        }
        writeItemSummaryValues(sheet, quantities, amounts);
    }

    /** 유효한 상단 '콩나물품목 / 수량 / 단가 / 금액' 구조가 있을 때만 갱신합니다. */
    static boolean writeItemSummaryValues(
            XSSFSheet sheet,
            Map<String, BigDecimal> quantities,
            Map<String, BigDecimal> amounts
    ) {
        if (sheet == null) {
            return false;
        }
        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        int headerRow = -1;
        int dateHeaderRow = -1;
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 80); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            String first = normalizeBillingLabel(
                    row.getCell(0) == null ? "" : formatter.formatCellValue(row.getCell(0))
            );
            if ("콩나물품목".equals(first)
                    && formattedEquals(row.getCell(1), formatter, "수량")
                    && formattedEquals(row.getCell(2), formatter, "단가")
                    && formattedEquals(row.getCell(3), formatter, "금액")) {
                headerRow = i;
            } else if ("날짜".equals(first)) {
                dateHeaderRow = i;
                break;
            }
        }
        if (headerRow < 0 || dateHeaderRow <= headerRow) {
            return false;
        }

        boolean updated = false;
        for (int i = headerRow + 1; i < dateHeaderRow; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            String raw = row.getCell(0) == null
                    ? "" : formatter.formatCellValue(row.getCell(0));
            String itemName = ItemCatalog.normalizeTemplateLabel(raw);
            if (itemName != null) {
                writeSummaryRow(row, itemName, quantities, amounts);
                updated = true;
            } else if (normalizeBillingLabel(raw).contains("회수통현황")) {
                Row returnRow = sheet.getRow(i + 1);
                if (returnRow != null && i + 1 < dateHeaderRow) {
                    writeSummaryRow(returnRow, "회수통", quantities, amounts);
                    updated = true;
                }
            }
        }
        return updated;
    }

    private static boolean formattedEquals(Cell cell, DataFormatter formatter, String value) {
        return cell != null && value.equals(normalizeBillingLabel(formatter.formatCellValue(cell)));
    }

    private static void writeSummaryRow(
            Row row,
            String itemName,
            Map<String, BigDecimal> quantities,
            Map<String, BigDecimal> amounts
    ) {
        BigDecimal quantity = quantities.getOrDefault(itemName, BigDecimal.ZERO);
        BigDecimal actual = amounts.getOrDefault(itemName, BigDecimal.ZERO);
        // 회수통은 차감 내역을 양수로 보여주고 최종 합계에서는 음수로 반영합니다.
        BigDecimal displayed = "회수통".equals(itemName) ? actual.negate() : actual;
        BigDecimal average = quantity.signum() == 0
                ? BigDecimal.ZERO
                : displayed.divide(quantity, 6, RoundingMode.HALF_UP);

        writeNumericCell(row, 1, quantity);
        writeNumericCell(row, 2, average);
        writeNumericCell(row, 3, displayed);
    }

    private static void writeNumericCell(Row row, int column, BigDecimal value) {
        Cell cell = row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        if (cell.getCellType() == CellType.FORMULA) {
            cell.setBlank();
        }
        cell.setCellValue(value.doubleValue());
    }

    /**
     * 달력과 같은 주문별 적용금액으로 명세서의 이달 합계금 및 최종 청구금액을 확정합니다.
     * '최종청구금액' 라벨 아래 행에 값이 들어있는 양식도 지원합니다.
     */
    static boolean writeFinalBillingAmount(
            XSSFSheet sheet,
            BigDecimal total
    ) {
        if (sheet == null) {
            return false;
        }

        DataFormatter formatter = new DataFormatter(Locale.KOREA);
        BigDecimal safeTotal = total == null ? BigDecimal.ZERO : total;
        boolean finalBillingWritten = false;

        for (Row row : sheet) {
            if (row == null) {
                continue;
            }
            for (Cell labelCell : row) {
                if (labelCell == null) {
                    continue;
                }
                String label = normalizeBillingLabel(
                        formatter.formatCellValue(labelCell)
                );
                boolean isFinalBilling = isFinalBillingLabel(label);
                boolean isMonthlyTotal = label.contains("이달합계금");
                if (!isFinalBilling && !isMonthlyTotal) {
                    continue;
                }

                Cell target = isFinalBilling
                        ? findFinalBillingValueCell(sheet, row, labelCell.getColumnIndex())
                        : findBillingValueCell(sheet, row, labelCell.getColumnIndex());
                if (target != null) {
                    // POI의 setCellValue()는 수식을 지우지 않고 캐시값만 바꿀 수 있다.
                    // 기존 수식을 먼저 제거해야 Excel 재계산 때 이전 금액으로 돌아가지 않는다.
                    if (target.getCellType() == CellType.FORMULA) {
                        target.setBlank();
                    }
                    target.setCellValue(safeTotal.doubleValue());
                    if (isFinalBilling) {
                        finalBillingWritten = true;
                    }
                }
            }
        }
        return finalBillingWritten;
    }

    /**
     * 현행 VIP마트 양식처럼 청구금액 라벨의 바로 아래 병합 셀에 금액이
     * 있는 경우를 우선하고, 같은 행 우측에 값이 있는 기존 양식도 지원합니다.
     */
    private static Cell findFinalBillingValueCell(
            XSSFSheet sheet,
            Row labelRow,
            int labelColumn
    ) {
        int firstColumn = labelColumn;
        int lastColumn = labelColumn;
        int lastLabelRow = labelRow.getRowNum();
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(labelRow.getRowNum(), labelColumn)) {
                firstColumn = region.getFirstColumn();
                lastColumn = region.getLastColumn();
                lastLabelRow = region.getLastRow();
                break;
            }
        }

        for (int rowIndex = lastLabelRow + 1;
             rowIndex <= Math.min(lastLabelRow + 3, sheet.getLastRowNum());
             rowIndex++) {
            Row valueRow = sheet.getRow(rowIndex);
            if (valueRow == null) {
                continue;
            }
            for (int column = firstColumn; column <= lastColumn; column++) {
                Cell cell = valueRow.getCell(column);
                if (cell != null && (cell.getCellType() == CellType.FORMULA
                        || cell.getCellType() == CellType.NUMERIC)) {
                    return cell;
                }
            }
        }
        return findBillingValueCell(sheet, labelRow, labelColumn);
    }

    private static Cell findBillingValueCell(
            XSSFSheet sheet,
            Row labelRow,
            int labelColumn
    ) {
        int firstValueColumn = labelColumn + 1;
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(labelRow.getRowNum(), labelColumn)) {
                firstValueColumn = Math.max(
                        firstValueColumn,
                        region.getLastColumn() + 1
                );
                break;
            }
        }

        int maxColumn = Math.max(
                Math.max(labelRow.getLastCellNum(), 0) + 8,
                firstValueColumn + 8
        );

        // 기존 템플릿의 계산식 셀이 있으면 그 셀을 가장 우선해서 교체한다.
        for (int column = firstValueColumn; column <= maxColumn; column++) {
            Cell cell = labelRow.getCell(column);
            if (cell != null && cell.getCellType() == CellType.FORMULA) {
                return cell;
            }
        }

        // 수식이 아닌 숫자 셀로 이미 저장된 템플릿도 지원한다.
        for (int column = firstValueColumn; column <= maxColumn; column++) {
            Cell cell = labelRow.getCell(column);
            if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                return cell;
            }
        }

        // 빈 값 셀만 남아 있는 양식이면 라벨 바로 오른쪽의 기존 스타일 셀을 사용한다.
        for (int column = firstValueColumn; column <= maxColumn; column++) {
            Cell cell = labelRow.getCell(column);
            if (cell != null
                    && (cell.getCellType() == CellType.BLANK
                    || cell.getCellType() == CellType.STRING
                    && cell.getStringCellValue().isBlank())) {
                return cell;
            }
        }

        return labelRow.createCell(firstValueColumn);
    }

    private static boolean isFinalBillingLabel(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        if (normalized.contains("최종청구금액")) {
            return true;
        }
        return normalized.contains("최종")
                && normalized.contains("청구")
                && normalized.contains("금액");
    }

    private static String normalizeBillingLabel(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("\\s+", "")
                .replace(":", "")
                .trim();
    }

    private StatementPeriod periodFor(
            String statementName,
            YearMonth month
    ) {
        if (SUNSAN_STATEMENT_NAME.equals(statementName)) {
            return new StatementPeriod(
                    month.minusMonths(1).atDay(26),
                    month.atDay(25)
            );
        }
        return new StatementPeriod(
                month.atDay(1),
                month.atEndOfMonth()
        );
    }

    private boolean within(
            LocalDate date,
            LocalDate start,
            LocalDate end
    ) {
        return date != null
                && !date.isBefore(start)
                && !date.isAfter(end);
    }

    private StatementWorkbookResult copyWithBytes(
            StatementWorkbookResult source,
            byte[] bytes
    ) {
        return new StatementWorkbookResult(
                bytes,
                source.filename(),
                source.generatedSheetCount(),
                source.sheetWithSalesCount(),
                source.removedEmptySheetCount(),
                source.warningCount()
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    @FunctionalInterface
    private interface BillingItemsResolver {
        List<SalesItemEntity> resolve(String sheetName);
    }

    private record StatementPeriod(
            LocalDate start,
            LocalDate end
    ) {
    }
}
