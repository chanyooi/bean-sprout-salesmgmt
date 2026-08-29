package com.example.salesmgmt.service;

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
                sheetName -> billingTotal(
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
        BigDecimal total = sumLineAmounts(periodItems);

        byte[] patched = patchWorkbook(result.fileBytes(), ignored -> total);
        return copyWithBytes(result, patched);
    }

    private byte[] patchWorkbook(
            byte[] source,
            BillingTotalResolver resolver
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
                BigDecimal total = resolver.resolve(sheet.getSheetName());
                writeFinalBillingAmount(sheet, total);
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

    private BigDecimal billingTotal(
            List<SalesItemEntity> items,
            StatementPeriod period
    ) {
        return sumLineAmounts(
                items.stream()
                        .filter(item -> within(
                                item.getSalesOrder().getDeliveryDate(),
                                period.start(),
                                period.end()
                        ))
                        .toList()
        );
    }

    private BigDecimal sumLineAmounts(List<SalesItemEntity> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (SalesItemEntity item : items) {
            if (item.getLineAmount() != null) {
                total = total.add(item.getLineAmount());
            }
        }
        return total;
    }

    /**
     * 템플릿에서 '최종 청구금액' 라벨을 찾아 같은 행의 값 셀을 숫자로 교체합니다.
     * 공백/줄바꿈이 들어간 라벨도 인식하고, 병합된 라벨 셀도 처리합니다.
     * 테스트에서 실제 template.xlsx를 열어 이 탐지가 되는지 검증합니다.
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
                if (!isFinalBillingLabel(label)) {
                    continue;
                }

                Cell target = findBillingValueCell(
                        sheet,
                        row,
                        labelCell.getColumnIndex()
                );
                if (target == null) {
                    return false;
                }

                target.setCellValue(safeTotal.doubleValue());
                return true;
            }
        }
        return false;
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
    private interface BillingTotalResolver {
        BigDecimal resolve(String sheetName);
    }

    private record StatementPeriod(
            LocalDate start,
            LocalDate end
    ) {
    }
}
