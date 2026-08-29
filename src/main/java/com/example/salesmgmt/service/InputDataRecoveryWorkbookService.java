package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.SalesOrderEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class InputDataRecoveryWorkbookService {

    private static final DateTimeFormatter SHEET_DATE_FORMAT =
            DateTimeFormatter.BASIC_ISO_DATE;
    private static final int VENDOR_COLUMN_INDEX = 2; // C

    private final InputTemplateWorkbookService inputTemplateWorkbookService;
    private final SalesItemRepository salesItemRepository;
    private final DataFormatter formatter = new DataFormatter(Locale.KOREA);

    public InputDataRecoveryWorkbookService(
            InputTemplateWorkbookService inputTemplateWorkbookService,
            SalesItemRepository salesItemRepository
    ) {
        this.inputTemplateWorkbookService = inputTemplateWorkbookService;
        this.salesItemRepository = salesItemRepository;
    }

    /**
     * 사이트 DB의 판매자료를 '정리데이터' 같은 별도 표로 내보내는 것이 아니라,
     * 매일 업로드하던 input_data.xlsx 원본 양식 위에 그대로 다시 채웁니다.
     *
     * A/B열 수식, 거래처 배치, 셀 스타일, 드롭다운 등은 master 양식을 유지하고
     * DB에 저장되어 있는 수량/회수통단가/전달방식/비고만 원래 입력 칸에 복원합니다.
     */
    @Transactional(readOnly = true)
    public Path createRecoveryWorkbookFile(
            YearMonth month,
            LocalDate endDate
    ) {
        validateRange(month, endDate);

        List<SalesItemEntity> items = salesItemRepository.findForMonthlyReport(
                month.atDay(1),
                endDate
        );

        Path blankWorkbook = inputTemplateWorkbookService.createBlankWorkbookFile(month);
        Path recoveredWorkbook = null;

        try {
            recoveredWorkbook = Files.createTempFile("input-data-recovery-", ".xlsx");

            try (InputStream input = Files.newInputStream(blankWorkbook);
                 XSSFWorkbook workbook = new XSSFWorkbook(input);
                 OutputStream output = Files.newOutputStream(recoveredWorkbook)) {

                Map<String, Map<String, Integer>> headerCache = new LinkedHashMap<>();

                for (SalesItemEntity item : items) {
                    SalesOrderEntity order = item.getSalesOrder();
                    Sheet sheet = resolveSheet(workbook, order);
                    Map<String, Integer> headers = headerCache.computeIfAbsent(
                            sheet.getSheetName(),
                            ignored -> findHeaderIndexes(sheet)
                    );

                    Row row = resolveVendorRow(sheet, order);
                    restoreItem(row, headers, item);
                    restoreOrderMetadata(row, headers, order);
                }

                workbook.setForceFormulaRecalculation(true);
                workbook.setActiveSheet(0);
                workbook.setSelectedTab(0);
                workbook.write(output);
            }

            return recoveredWorkbook;
        } catch (IOException exception) {
            deleteQuietly(recoveredWorkbook);
            throw new IllegalStateException(
                    "현재 장부를 input_data.xlsx 양식으로 복구하는 중 오류가 발생했습니다.",
                    exception
            );
        } catch (RuntimeException exception) {
            deleteQuietly(recoveredWorkbook);
            throw exception;
        } finally {
            deleteQuietly(blankWorkbook);
        }
    }

    private Sheet resolveSheet(
            XSSFWorkbook workbook,
            SalesOrderEntity order
    ) {
        String sourceSheet = trimToNull(order.getSourceSheet());
        if (sourceSheet != null) {
            Sheet exact = workbook.getSheet(sourceSheet);
            if (exact != null) {
                return exact;
            }
        }

        String dateSheet = order.getDeliveryDate().format(SHEET_DATE_FORMAT);
        Sheet fallback = workbook.getSheet(dateSheet);
        if (fallback != null) {
            return fallback;
        }

        throw new IllegalStateException(
                "복구할 날짜 시트를 찾지 못했습니다: " + order.getDeliveryDate()
        );
    }

    private Row resolveVendorRow(
            Sheet sheet,
            SalesOrderEntity order
    ) {
        String vendorName = normalize(order.getVendor().getInputName());

        Integer sourceRow = order.getSourceRow();
        if (sourceRow != null && sourceRow > 0) {
            Row exact = sheet.getRow(sourceRow - 1);
            if (exact != null) {
                String rowVendor = normalize(formatted(exact.getCell(VENDOR_COLUMN_INDEX)));
                if (vendorName.equals(rowVendor)) {
                    return exact;
                }
            }
        }

        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            String rowVendor = normalize(formatted(row.getCell(VENDOR_COLUMN_INDEX)));
            if (vendorName.equals(rowVendor)) {
                return row;
            }
        }

        throw new IllegalStateException(
                "원본 input_data.xlsx 양식에서 거래처를 찾지 못했습니다: "
                        + order.getVendor().getInputName()
                        + " (" + order.getDeliveryDate() + ")"
        );
    }

    private void restoreItem(
            Row row,
            Map<String, Integer> headers,
            SalesItemEntity item
    ) {
        Integer column = headers.get(normalize(item.getItemName()));
        if (column == null) {
            throw new IllegalStateException(
                    "원본 input_data.xlsx 양식에 품목 열이 없습니다: " + item.getItemName()
            );
        }

        BigDecimal quantity = item.getQuantity();
        if (quantity != null && quantity.signum() != 0) {
            getCell(row, column).setCellValue(quantity.doubleValue());
        }
    }

    private void restoreOrderMetadata(
            Row row,
            Map<String, Integer> headers,
            SalesOrderEntity order
    ) {
        setNumberIfPresent(
                row,
                headers.get("회수통단가"),
                order.getReturnContainerUnitPrice()
        );
        setTextIfPresent(
                row,
                headers.get("전달방식"),
                order.getDeliveryMethod()
        );
        setTextIfPresent(
                row,
                headers.get("비고"),
                order.getNote()
        );
    }

    private Map<String, Integer> findHeaderIndexes(Sheet sheet) {
        int limit = Math.min(sheet.getLastRowNum(), 20);
        for (int rowIndex = 0; rowIndex <= limit; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            Map<String, Integer> result = new LinkedHashMap<>();
            int lastCell = Math.max(row.getLastCellNum(), 0);
            for (int column = 0; column < lastCell; column++) {
                String label = normalize(formatted(row.getCell(column)));
                if (!label.isBlank()) {
                    result.putIfAbsent(label, column);
                }
            }

            if (result.containsKey("주문번호")
                    && result.containsKey("날짜")
                    && result.containsKey("거래처")) {
                return result;
            }
        }

        throw new IllegalStateException(
                sheet.getSheetName() + " 시트에서 input_data.xlsx 머리글을 찾지 못했습니다."
        );
    }

    private void setNumberIfPresent(
            Row row,
            Integer column,
            BigDecimal value
    ) {
        if (column == null || value == null) {
            return;
        }
        getCell(row, column).setCellValue(value.doubleValue());
    }

    private void setTextIfPresent(
            Row row,
            Integer column,
            String value
    ) {
        if (column == null || value == null || value.isBlank()) {
            return;
        }
        getCell(row, column).setCellValue(value.trim());
    }

    private Cell getCell(Row row, int column) {
        Cell cell = row.getCell(column);
        return cell == null ? row.createCell(column) : cell;
    }

    private String formatted(Cell cell) {
        return cell == null ? "" : formatter.formatCellValue(cell);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void validateRange(YearMonth month, LocalDate endDate) {
        if (month == null || endDate == null) {
            throw new IllegalArgumentException("복구할 정산월과 기준일을 선택해주세요.");
        }
        if (!YearMonth.from(endDate).equals(month)) {
            throw new IllegalArgumentException(
                    "기준일은 선택한 정산월 안의 날짜여야 합니다."
            );
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
