package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.StandardStatementSchema;
import com.example.salesmgmt.domain.StandardStatementSchema.Field;
import com.example.salesmgmt.domain.StandardStatementSchema.Kind;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Service
public class StandardStatementExcelService {
    private final StandardStatementService service;

    public StandardStatementExcelService(StandardStatementService service) { this.service = service; }

    /** 기존 월별 입력/분개 파일을 보존하고 표준재무제표 3개 시트를 앞에 추가합니다. */
    public byte[] addStandardSheets(int year, byte[] existingWorkbook) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(existingWorkbook));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Map<String, BigDecimal> manual = service.entered(year);
            Map<String, BigDecimal> values = StandardStatementSchema.evaluate(manual);

            for (Kind kind : Kind.values()) addStatementSheet(book, year, kind, manual, values);
            addGuidanceSheet(book);
            book.setSheetOrder(Kind.BALANCE.getLabel(), 0);
            book.setSheetOrder(Kind.INCOME.getLabel(), 1);
            book.setSheetOrder(Kind.COST.getLabel(), 2);
            book.setSheetOrder("양식안내", 3);
            book.setForceFormulaRecalculation(true);
            book.write(out);
            return out.toByteArray();
        }
    }

    private static void addStatementSheet(XSSFWorkbook book, int year, Kind kind,
                                         Map<String, BigDecimal> manual,
                                         Map<String, BigDecimal> calculated) {
        Sheet sheet = book.createSheet(kind.getLabel());
        CellStyle titleStyle = makeStyle(book, IndexedColors.DARK_BLUE, true, false);
        CellStyle headingStyle = makeStyle(book, IndexedColors.BLUE_GREY, true, false);
        CellStyle subtotalStyle = makeStyle(book, IndexedColors.PALE_BLUE, true, true);
        CellStyle enteredStyle = makeStyle(book, IndexedColors.LIGHT_YELLOW, false, true);
        CellStyle numericStyle = makeStyle(book, null, false, true);

        Row first = sheet.createRow(0);
        put(first, 0, kind.getLabel() + " (수기 입력 참고용)", titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
        sheet.createRow(1).createCell(0).setCellValue("송천콩나물  |  " + year + ".01.01 ~ " + year + ".12.31  |  단위: 원");
        sheet.createRow(2).createCell(0).setCellValue("2025년 제출된 표준재무제표증명 계정코드 기준. 공식 발급 증명서나 홈택스 신고 파일은 아닙니다.");
        sheet.createRow(3).createCell(0).setCellValue(kind == Kind.COST
                ? "제조원가명세서 형식(2025년 제출 양식과 동일) / 실제 적용 서식은 신고 시점에 확인"
                : kind == Kind.INCOME ? "당기총원가(코드 17)는 원가명세서 코드 42와 연결"
                                     : "당기순이익(코드 89)은 손익계산서 코드 99와 연결");

        Row headings = sheet.createRow(4);
        put(headings, 0, "코드", headingStyle);
        put(headings, 1, "계정과목", headingStyle);
        put(headings, 2, "금액(원)", headingStyle);
        put(headings, 3, "구분", headingStyle);

        List<Field> fields = StandardStatementSchema.fields(kind);
        Map<String, Integer> excelRows = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) excelRows.put(fields.get(i).code(), i + 6);

        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            Row row = sheet.createRow(i + 5);
            row.setHeightInPoints(field.indent() == 0 ? 23 : 21);
            row.createCell(0).setCellValue(field.code());
            Cell name = row.createCell(1);
            name.setCellValue(field.label());
            CellStyle nameStyle = book.createCellStyle();
            nameStyle.setIndention((short)Math.min(10, field.indent() * 2));
            if (field.indent() == 0) {
                Font f = book.createFont();
                f.setBold(true);
                nameStyle.setFont(f);
            }
            name.setCellStyle(nameStyle);

            Cell value = row.createCell(2);
            String key = StandardStatementSchema.key(kind, field.code());
            if (field.computed()) {
                value.setCellFormula(toExcelFormula(kind, field, excelRows));
                value.setCellStyle(subtotalStyle);
                row.createCell(3).setCellValue("자동 계산");
            } else {
                BigDecimal amount = manual.get(key);
                if (amount != null) value.setCellValue(amount.doubleValue());
                value.setCellStyle(enteredStyle);
                row.createCell(3).setCellValue("직접 입력");
            }
        }
        int last = fields.size() + 5;
        Row footer = sheet.createRow(last + 1);
        if (kind == Kind.BALANCE) {
            put(footer, 1, "자산총계 - 부채및자본총계 (0이면 일치)", headingStyle);
            Cell diff = footer.createCell(2);
            diff.setCellFormula("C" + excelRows.get("62") + "-C" + excelRows.get("91"));
            diff.setCellStyle(numericStyle);
        } else if (kind == Kind.INCOME) {
            put(footer, 1, "당기순손익", headingStyle);
            Cell net = footer.createCell(2);
            net.setCellFormula("C" + excelRows.get("99"));
            net.setCellStyle(numericStyle);
        } else {
            put(footer, 1, "당기제품제조원가", headingStyle);
            Cell cost = footer.createCell(2);
            cost.setCellFormula("C" + excelRows.get("42"));
            cost.setCellStyle(numericStyle);
        }

        sheet.createFreezePane(2, 5);
        sheet.setColumnWidth(0, 11 * 256);
        sheet.setColumnWidth(1, 48 * 256);
        sheet.setColumnWidth(2, 23 * 256);
        sheet.setColumnWidth(3, 16 * 256);
        sheet.setAutoFilter(new CellRangeAddress(4, last - 1, 0, 3));
    }

    private static String toExcelFormula(Kind kind, Field field, Map<String, Integer> sameSheetRows) {
        StringBuilder out = new StringBuilder();
        for (String token : field.formula().split(" +")) {
            boolean minus = token.charAt(0) == '-';
            String ref = token.substring(1);
            Kind target = kind;
            if (ref.contains(":")) {
                String[] pair = ref.split(":", 2);
                target = Kind.valueOf(pair[0]);
                ref = pair[1];
            }
            int from, to;
            if (ref.contains("..")) {
                String[] endpoints = ref.split("\\.\\.", 2);
                from = Integer.parseInt(endpoints[0]);
                to = Integer.parseInt(endpoints[1]);
            } else {
                from = to = Integer.parseInt(ref);
            }
            for (int code = from; code <= to; code++) {
                if (!out.isEmpty()) out.append(minus ? "-" : "+");
                else if (minus) out.append("-");
                String formatted = String.format("%02d", code);
                if (target == kind) out.append("C").append(sameSheetRows.get(formatted));
                else {
                    int foreignRow = 6;
                    for (Field foreign : StandardStatementSchema.fields(target)) {
                        if (foreign.code().equals(formatted)) break;
                        foreignRow++;
                    }
                    out.append("'").append(target.getLabel()).append("'!C").append(foreignRow);
                }
            }
        }
        return out.toString();
    }

    private static void addGuidanceSheet(XSSFWorkbook book) {
        Sheet sheet = book.createSheet("양식안내");
        CellStyle headline = makeStyle(book, IndexedColors.DARK_BLUE, true, false);
        put(sheet.createRow(0), 0, "표준재무제표 서식 안내", headline);
        String[] messages = {
                "이 파일은 송천콩나물 사이트에서 직접 입력한 연간 금액을 바탕으로 작성된 참고용 엑셀입니다.",
                "홈택스에서 발급하는 표준재무제표증명 원본이나 전자신고에 바로 업로드할 수 있는 파일은 아닙니다.",
                "계정과목과 코드 및 3종 서식은 송천콩나물의 2025년 표준재무제표증명을 기준으로 구성했습니다.",
                "표준재무상태표: 자산총계(62)와 부채·자본총계(91)를 비교해 차액 0원을 확인하세요.",
                "표준손익계산서: 당기총원가(17)는 원가명세서의 당기제품제조원가(42)를 참조합니다.",
                "표준재무상태표: 당기순이익(89)은 표준손익계산서의 당기순손익(99)을 참조합니다.",
                "표준원가명세서: 제조경비와 판관비는 구분해서 작성하고 동일 지출을 중복 계상하지 마세요.",
                "재료비는 기초재료재고액 + 당기재료매입액 - 기말재료재고액 - 타계정대체액입니다.",
                "기말 자산·부채는 통장, 외상채권, 보증금 및 재고 등의 실제 잔액과 일치해야 합니다.",
                "기존 '월별금액입력', '분개장', '분개계정별집계', '작성안내' 시트는 별도 수기 장부 자료입니다.",
                "기존 월별 수기 자료와 표준재무제표 서식 입력 자료는 자동 연동하지 않으므로 금액을 대조하세요.",
                "2025년 서식의 제조업 표준원가명세서 표현을 유지했지만 적용 신고서식은 해당 연도에 재확인하세요."
        };
        for (int i = 0; i < messages.length; i++) sheet.createRow(i + 2).createCell(0).setCellValue(messages[i]);
        sheet.setColumnWidth(0, 130 * 256);
    }

    private static CellStyle makeStyle(Workbook book, IndexedColors fill, boolean bold, boolean money) {
        CellStyle style = book.createCellStyle();
        Font font = book.createFont();
        font.setBold(bold);
        if (fill == IndexedColors.DARK_BLUE || fill == IndexedColors.BLUE_GREY) font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        if (fill != null) {
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setFillForegroundColor(fill.getIndex());
        }
        if (money) style.setDataFormat(book.createDataFormat().getFormat("#,##0;[Red](#,##0);-"));
        return style;
    }

    private static void put(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }
}
