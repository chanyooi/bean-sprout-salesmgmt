package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.ManualBookkeepingAccounts;
import com.example.salesmgmt.domain.ManualBookkeepingCategory;
import com.example.salesmgmt.entity.ManualBookkeepingMonthlyEntity;
import com.example.salesmgmt.entity.ManualJournalEntryEntity;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ManualBookkeepingExcelService {
    public byte[] export(int year, List<ManualBookkeepingMonthlyEntity> monthly,
                         List<ManualJournalEntryEntity> journal) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle titleStyle = style(workbook, IndexedColors.DARK_BLUE, true, false);
            CellStyle headerStyle = style(workbook, IndexedColors.BLUE_GREY, true, false);
            CellStyle amountStyle = style(workbook, null, false, true);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));

            addMonthlySheet(workbook, year, monthly, titleStyle, headerStyle, amountStyle);
            addJournalSheet(workbook, year, journal, titleStyle, headerStyle, amountStyle, dateStyle);
            addAccountsSheet(workbook, journal, titleStyle, headerStyle, amountStyle);
            addHelpSheet(workbook, titleStyle, headerStyle);
            workbook.setForceFormulaRecalculation(true);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void addMonthlySheet(Workbook book, int year, List<ManualBookkeepingMonthlyEntity> data,
                                 CellStyle title, CellStyle header, CellStyle number) {
        Sheet sheet = book.createSheet("월별금액입력");
        Row heading = sheet.createRow(0);
        Cell t = heading.createCell(0);
        t.setCellValue(year + "년 송천콩나물 월별 수기 금액");
        t.setCellStyle(title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 13));
        sheet.createRow(1).createCell(0).setCellValue("이 시트는 월별 요약이며 단독으로 복식부기 분개장이나 세무신고 서식이 아닙니다.");

        Row cols = sheet.createRow(3);
        put(cols, 0, "항목", header);
        for (int month = 1; month <= 12; month++) put(cols, month, month + "월", header);
        put(cols, 13, "연간 합계", header);

        Map<String, BigDecimal> values = new HashMap<>();
        for (ManualBookkeepingMonthlyEntity item : data) {
            values.put(item.getCategoryCode() + ":" + item.getMonth(), item.getAmount());
        }
        int rowNumber = 4;
        for (ManualBookkeepingCategory category : ManualBookkeepingCategory.values()) {
            Row row = sheet.createRow(rowNumber);
            row.createCell(0).setCellValue(category.getLabel());
            for (int month = 1; month <= 12; month++) {
                BigDecimal value = values.get(category.name() + ":" + month);
                if (value != null) putNumber(row, month, value, number);
            }
            Cell yearTotal = row.createCell(13);
            yearTotal.setCellFormula("SUM(B" + (rowNumber + 1) + ":M" + (rowNumber + 1) + ")");
            yearTotal.setCellStyle(number);
            rowNumber++;
        }
        Row net = sheet.createRow(rowNumber + 1);
        put(net, 0, "참고 차액(매출-입력경비)", header);
        for (int month = 1; month <= 13; month++) {
            String column = CellReference.convertNumToColString(month);
            int salesRow = 5;
            int firstCostRow = 6;
            int lastCostRow = rowNumber;
            Cell difference = net.createCell(month);
            difference.setCellFormula(column + salesRow + "-SUM(" + column + firstCostRow +
                    ":" + column + lastCostRow + ")");
            difference.setCellStyle(number);
        }
        sheet.createFreezePane(1, 4);
        sheet.setColumnWidth(0, 24 * 256);
        for (int c = 1; c <= 13; c++) sheet.setColumnWidth(c, 17 * 256);
        sheet.setAutoFilter(new CellRangeAddress(3, rowNumber - 1, 0, 13));
    }

    private void addJournalSheet(Workbook book, int year, List<ManualJournalEntryEntity> entries,
                                 CellStyle title, CellStyle header, CellStyle number, CellStyle dateStyle) {
        Sheet sheet = book.createSheet("분개장");
        put(sheet.createRow(0), 0, year + "년 수기 분개장", title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
        sheet.createRow(1).createCell(0).setCellValue("한 거래가 차변·대변 2행으로 기록됩니다. 월별금액입력 시트와 자동 연동하지 않습니다.");
        Row cols = sheet.createRow(3);
        String[] labels = {"전표번호", "거래일", "적요", "계정과목", "차변", "대변"};
        for (int c = 0; c < labels.length; c++) put(cols, c, labels[c], header);
        int rowNumber = 4;
        for (ManualJournalEntryEntity entry : entries) {
            Row debit = sheet.createRow(rowNumber++);
            fillJournalRow(debit, entry, entry.getDebitAccount(), true, number, dateStyle);
            Row credit = sheet.createRow(rowNumber++);
            fillJournalRow(credit, entry, entry.getCreditAccount(), false, number, dateStyle);
        }
        int lastDataRow = rowNumber;
        // 사용자가 엑셀에서 직접 추가로 입력할 수 있는 빈 행
        for (int i = 0; i < 20; i++) sheet.createRow(rowNumber++);
        Row total = sheet.createRow(rowNumber);
        put(total, 3, "합계", header);
        for (int c = 4; c <= 5; c++) {
            Cell cell = total.createCell(c);
            String col = CellReference.convertNumToColString(c);
            cell.setCellFormula("SUM(" + col + "5:" + col + (rowNumber) + ")");
            cell.setCellStyle(number);
        }
        sheet.createFreezePane(0, 4);
        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 16 * 256);
        sheet.setColumnWidth(2, 34 * 256);
        sheet.setColumnWidth(3, 23 * 256);
        sheet.setColumnWidth(4, 18 * 256);
        sheet.setColumnWidth(5, 18 * 256);
        if (lastDataRow > 4) sheet.setAutoFilter(new CellRangeAddress(3, lastDataRow - 1, 0, 5));
    }

    private void fillJournalRow(Row row, ManualJournalEntryEntity entry, String account,
                                boolean debit, CellStyle number, CellStyle dateStyle) {
        row.createCell(0).setCellValue(entry.getId() == null ? "" : entry.getId().toString());
        Cell date = row.createCell(1);
        date.setCellValue(entry.getEntryDate());
        date.setCellStyle(dateStyle);
        row.createCell(2).setCellValue(entry.getDescription());
        row.createCell(3).setCellValue(account);
        putNumber(row, debit ? 4 : 5, entry.getAmount(), number);
    }

    private void addAccountsSheet(Workbook book, List<ManualJournalEntryEntity> entries,
                                  CellStyle title, CellStyle header, CellStyle number) {
        Sheet sheet = book.createSheet("분개계정별집계");
        put(sheet.createRow(0), 0, "분개장 기반 계정별 합계", title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
        sheet.createRow(1).createCell(0).setCellValue("기초잔액·결산조정은 포함하지 않습니다. 시산표를 확정하는 자료가 아닙니다.");
        Row cols = sheet.createRow(3);
        for (int c = 0; c < 4; c++) put(cols, c, new String[]{"계정과목", "차변 합계", "대변 합계", "차변-대변"}[c], header);
        Map<String, BigDecimal[]> totals = new HashMap<>();
        for (String account : ManualBookkeepingAccounts.ALL)
            totals.put(account, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        for (ManualJournalEntryEntity entry : entries) {
            totals.get(entry.getDebitAccount())[0] = totals.get(entry.getDebitAccount())[0].add(entry.getAmount());
            totals.get(entry.getCreditAccount())[1] = totals.get(entry.getCreditAccount())[1].add(entry.getAmount());
        }
        int index = 4;
        for (String account : ManualBookkeepingAccounts.ALL) {
            Row row = sheet.createRow(index++);
            row.createCell(0).setCellValue(account);
            BigDecimal[] parts = totals.get(account);
            putNumber(row, 1, parts[0], number);
            putNumber(row, 2, parts[1], number);
            Cell balance = row.createCell(3);
            balance.setCellFormula("B" + index + "-C" + index);
            balance.setCellStyle(number);
        }
        Row total = sheet.createRow(index);
        put(total, 0, "합계", header);
        for (int col = 1; col <= 3; col++) {
            Cell cell = total.createCell(col);
            String colLetter = CellReference.convertNumToColString(col);
            cell.setCellFormula("SUM(" + colLetter + "5:" + colLetter + index + ")");
            cell.setCellStyle(number);
        }
        sheet.createFreezePane(1, 4);
        sheet.setColumnWidth(0, 25 * 256);
        for (int col = 1; col <= 3; col++) sheet.setColumnWidth(col, 19 * 256);
    }

    private void addHelpSheet(Workbook book, CellStyle title, CellStyle header) {
        Sheet sheet = book.createSheet("작성안내");
        put(sheet.createRow(0), 0, "송천콩나물 수기 장부 작성 안내", title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
        String[] notes = {
                "1. 월별금액입력: 웹 메뉴에서 직접 입력한 월별 매출·경비 금액을 그대로 내보냅니다.",
                "2. 분개장: 웹에서 입력한 차변/대변 거래를 두 행으로 기록합니다.",
                "3. 계정별집계: 분개장에 실제 입력한 전표만 계산하며 월별금액입력과 합산하지 않습니다.",
                "4. 매출·원재료비 등 월별 금액은 결산조정, 재고, 미수금 등을 반영하기 전 금액일 수 있습니다.",
                "5. 같은 거래를 계산서와 카드 지출로 중복하여 입력하지 않도록 주의하세요.",
                "6. 이 파일은 홈택스 전자신고 파일이 아니며 공식 표준재무제표/세무조정계산서가 아닙니다.",
                "7. 연말에는 예금, 매출채권, 매입채무, 재고, 보증금 등 기초·기말 잔액을 따로 확인하세요."
        };
        put(sheet.createRow(2), 0, "주의사항", header);
        for (int i = 0; i < notes.length; i++) sheet.createRow(3 + i).createCell(0).setCellValue(notes[i]);
        sheet.setColumnWidth(0, 115 * 256);
    }

    private static CellStyle style(Workbook workbook, IndexedColors color, boolean bold, boolean amount) {
        CellStyle s = workbook.createCellStyle();
        Font f = workbook.createFont();
        f.setBold(bold);
        if (color != null) {
            f.setColor(IndexedColors.WHITE.getIndex());
            s.setFillForegroundColor(color.getIndex());
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        s.setFont(f);
        if (amount) s.setDataFormat(workbook.createDataFormat().getFormat("#,##0;[Red](#,##0);-"));
        return s;
    }

    private static void put(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void putNumber(Row row, int index, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }
}
