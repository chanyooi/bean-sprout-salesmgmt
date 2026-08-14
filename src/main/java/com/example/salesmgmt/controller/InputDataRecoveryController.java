package com.example.salesmgmt.controller;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Controller
public class InputDataRecoveryController {

    private final ApplicationContext applicationContext;

    public InputDataRecoveryController(
            ApplicationContext applicationContext
    ) {
        this.applicationContext = applicationContext;
    }

    @GetMapping("/input-data/recovery")
    public ResponseEntity<byte[]> downloadRecoveryWorkbook(
            @RequestParam String month,
            @RequestParam(required = false) String through
    ) {
        YearMonth yearMonth = YearMonth.parse(month);

        LocalDate endDate =
                (through == null || through.isBlank())
                        ? yearMonth.atEndOfMonth()
                        : LocalDate.parse(through);

        if (!YearMonth.from(endDate).equals(yearMonth)) {
            throw new IllegalArgumentException(
                    "기준일은 선택한 정산월 안의 날짜여야 합니다."
            );
        }

        List<RecoveryRow> rows =
                loadRowsFromSalesManagementService(
                        yearMonth,
                        endDate
                );

        byte[] workbook = createWorkbook(
                yearMonth,
                endDate,
                rows
        );

        String fileName =
                "input_data_복구_"
                        + endDate
                        + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
        );
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(
                                fileName,
                                StandardCharsets.UTF_8
                        )
                        .build()
        );

        return ResponseEntity.ok()
                .headers(headers)
                .body(workbook);
    }

    private List<RecoveryRow> loadRowsFromSalesManagementService(
            YearMonth month,
            LocalDate endDate
    ) {
        Object service =
                applicationContext.getBean(
                        "salesManagementService"
                );

        Object rawRows =
                invokeFindRows(
                        service,
                        month
                );

        if (!(rawRows instanceof Iterable<?> iterable)) {
            throw new IllegalStateException(
                    "판매내역 조회 결과를 읽을 수 없습니다."
            );
        }

        List<RecoveryRow> result =
                new ArrayList<>();

        for (Object row : iterable) {
            if (row == null) {
                continue;
            }

            LocalDate date =
                    readDate(
                            row,
                            "date",
                            "deliveryDate",
                            "orderDate"
                    );

            if (date == null
                    || date.isBefore(month.atDay(1))
                    || date.isAfter(endDate)) {
                continue;
            }

            String orderNo =
                    readString(
                            row,
                            "orderNo",
                            "orderNumber",
                            "orderId"
                    );

            String vendor =
                    readString(
                            row,
                            "vendor",
                            "vendorName",
                            "inputName"
                    );

            String item =
                    readString(
                            row,
                            "item",
                            "itemName"
                    );

            BigDecimal quantity =
                    readNumber(
                            row,
                            "quantity",
                            "qty"
                    );

            String deliveryMethod =
                    readString(
                            row,
                            "deliveryMethod",
                            "delivery"
                    );

            String note =
                    readString(
                            row,
                            "note",
                            "remark",
                            "memo"
                    );

            result.add(
                    new RecoveryRow(
                            orderNo,
                            date,
                            vendor,
                            item,
                            quantity,
                            deliveryMethod,
                            note
                    )
            );
        }

        result.sort(
                Comparator
                        .comparing(RecoveryRow::date)
                        .thenComparing(
                                row -> nullToEmpty(
                                        row.orderNo()
                                )
                        )
                        .thenComparing(
                                row -> nullToEmpty(
                                        row.vendor()
                                )
                        )
                        .thenComparing(
                                row -> nullToEmpty(
                                        row.item()
                                )
                        )
        );

        return result;
    }

    private Object invokeFindRows(
            Object service,
            YearMonth month
    ) {
        for (Method method
                : service.getClass().getMethods()) {

            if (!method.getName().equals("findRows")) {
                continue;
            }

            Class<?>[] parameterTypes =
                    method.getParameterTypes();

            try {
                if (parameterTypes.length == 2
                        && YearMonth.class.isAssignableFrom(
                                parameterTypes[0]
                        )) {
                    return method.invoke(
                            service,
                            month,
                            null
                    );
                }

                if (parameterTypes.length == 1
                        && YearMonth.class.isAssignableFrom(
                                parameterTypes[0]
                        )) {
                    return method.invoke(
                            service,
                            month
                    );
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(
                        "판매내역을 불러오지 못했습니다.",
                        exception
                );
            }
        }

        throw new IllegalStateException(
                "SalesManagementService.findRows 메서드를 찾지 못했습니다."
        );
    }

    private byte[] createWorkbook(
            YearMonth month,
            LocalDate endDate,
            List<RecoveryRow> rows
    ) {
        try (XSSFWorkbook workbook =
                     new XSSFWorkbook();
             ByteArrayOutputStream outputStream =
                     new ByteArrayOutputStream()) {

            Sheet sheet =
                    workbook.createSheet(
                            "정리데이터"
                    );

            CellStyle headerStyle =
                    workbook.createCellStyle();

            Font headerFont =
                    workbook.createFont();

            headerFont.setBold(true);
            headerFont.setColor(
                    IndexedColors.WHITE.getIndex()
            );

            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(
                    IndexedColors.DARK_BLUE.getIndex()
            );
            headerStyle.setFillPattern(
                    FillPatternType.SOLID_FOREGROUND
            );
            headerStyle.setAlignment(
                    HorizontalAlignment.CENTER
            );
            headerStyle.setVerticalAlignment(
                    VerticalAlignment.CENTER
            );

            CellStyle dateStyle =
                    workbook.createCellStyle();

            CreationHelper creationHelper =
                    workbook.getCreationHelper();

            dateStyle.setDataFormat(
                    creationHelper
                            .createDataFormat()
                            .getFormat(
                                    "yyyy-mm-dd"
                            )
            );

            Row title = sheet.createRow(0);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue(
                    "DB 복구 장부"
            );

            Row info = sheet.createRow(1);
            info.createCell(0).setCellValue(
                    month + " / "
                            + endDate
                            + "까지"
            );

            Row header = sheet.createRow(3);

            String[] columns = {
                    "주문번호",
                    "날짜",
                    "거래처",
                    "품목",
                    "수량",
                    "전달방식",
                    "비고"
            };

            for (int i = 0;
                 i < columns.length;
                 i++) {

                Cell cell =
                        header.createCell(i);

                cell.setCellValue(
                        columns[i]
                );
                cell.setCellStyle(
                        headerStyle
                );
            }

            int rowIndex = 4;

            for (RecoveryRow value : rows) {
                Row row =
                        sheet.createRow(
                                rowIndex++
                        );

                row.createCell(0)
                        .setCellValue(
                                nullToEmpty(
                                        value.orderNo()
                                )
                        );

                Cell dateCell =
                        row.createCell(1);

                dateCell.setCellValue(
                        Date.from(
                                value.date()
                                        .atStartOfDay(
                                                ZoneId.systemDefault()
                                        )
                                        .toInstant()
                        )
                );

                dateCell.setCellStyle(
                        dateStyle
                );

                row.createCell(2)
                        .setCellValue(
                                nullToEmpty(
                                        value.vendor()
                                )
                        );

                row.createCell(3)
                        .setCellValue(
                                nullToEmpty(
                                        value.item()
                                )
                        );

                Cell quantityCell =
                        row.createCell(4);

                if (value.quantity() != null) {
                    quantityCell.setCellValue(
                            value.quantity()
                                    .doubleValue()
                    );
                }

                row.createCell(5)
                        .setCellValue(
                                nullToEmpty(
                                        value.deliveryMethod()
                                )
                        );

                row.createCell(6)
                        .setCellValue(
                                nullToEmpty(
                                        value.note()
                                )
                        );
            }

            sheet.createFreezePane(
                    0,
                    4
            );

            sheet.setAutoFilter(
                    new org.apache.poi.ss.util.CellRangeAddress(
                            3,
                            Math.max(
                                    3,
                                    rowIndex - 1
                            ),
                            0,
                            columns.length - 1
                    )
            );

            int[] widths = {
                    18,
                    13,
                    28,
                    18,
                    11,
                    16,
                    30
            };

            for (int i = 0;
                 i < widths.length;
                 i++) {
                sheet.setColumnWidth(
                        i,
                        widths[i] * 256
                );
            }

            Sheet guide =
                    workbook.createSheet(
                            "복구안내"
                    );

            guide.createRow(0)
                    .createCell(0)
                    .setCellValue(
                            "이 파일은 사이트 DB에 저장된 거래내역을 다시 엑셀로 복구한 파일입니다."
                    );

            guide.createRow(2)
                    .createCell(0)
                    .setCellValue(
                            "복구 범위"
                    );

            guide.getRow(2)
                    .createCell(1)
                    .setCellValue(
                            month.atDay(1)
                                    + " ~ "
                                    + endDate
                    );

            guide.createRow(3)
                    .createCell(0)
                    .setCellValue(
                            "복구 행 수"
                    );

            guide.getRow(3)
                    .createCell(1)
                    .setCellValue(
                            rows.size()
                    );

            guide.createRow(5)
                    .createCell(0)
                    .setCellValue(
                            "주의: 원본 input_data.xlsx의 특수 배치/수식이 있다면 원본 템플릿과 모양은 다를 수 있습니다."
                    );

            guide.setColumnWidth(
                    0,
                    75 * 256
            );
            guide.setColumnWidth(
                    1,
                    25 * 256
            );

            workbook.write(
                    outputStream
            );

            return outputStream.toByteArray();

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "복구 엑셀 생성에 실패했습니다.",
                    exception
            );
        }
    }

    private Object readValue(
            Object target,
            String... candidates
    ) {
        for (String name : candidates) {
            try {
                Method method =
                        target.getClass()
                                .getMethod(name);

                return method.invoke(
                        target
                );
            } catch (ReflectiveOperationException ignored) {
            }

            try {
                String getter =
                        "get"
                                + name.substring(
                                        0,
                                        1
                                ).toUpperCase(
                                        Locale.ROOT
                                )
                                + name.substring(1);

                Method method =
                        target.getClass()
                                .getMethod(getter);

                return method.invoke(
                        target
                );
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }

    private String readString(
            Object row,
            String... candidates
    ) {
        Object value =
                readValue(
                        row,
                        candidates
                );

        return value == null
                ? ""
                : String.valueOf(value);
    }

    private BigDecimal readNumber(
            Object row,
            String... candidates
    ) {
        Object value =
                readValue(
                        row,
                        candidates
                );

        if (value == null) {
            return BigDecimal.ZERO;
        }

        if (value instanceof BigDecimal decimal) {
            return decimal;
        }

        if (value instanceof Number number) {
            return BigDecimal.valueOf(
                    number.doubleValue()
            );
        }

        try {
            return new BigDecimal(
                    String.valueOf(value)
            );
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate readDate(
            Object row,
            String... candidates
    ) {
        Object value =
                readValue(
                        row,
                        candidates
                );

        if (value instanceof LocalDate localDate) {
            return localDate;
        }

        if (value != null) {
            try {
                return LocalDate.parse(
                        String.valueOf(value)
                );
            } catch (RuntimeException ignored) {
            }
        }

        return null;
    }

    private static String nullToEmpty(
            String value
    ) {
        return value == null
                ? ""
                : value;
    }

    private record RecoveryRow(
            String orderNo,
            LocalDate date,
            String vendor,
            String item,
            BigDecimal quantity,
            String deliveryMethod,
            String note
    ) {
    }
}
