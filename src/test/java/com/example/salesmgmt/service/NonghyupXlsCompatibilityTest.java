package com.example.salesmgmt.service;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFClientAnchor;
import org.apache.poi.hssf.usermodel.HSSFSimpleShape;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

class NonghyupXlsCompatibilityTest {
    private static final byte[] LEGACY_PADDING = {7, 0, 0, 0, 8, 0, 0, 0};

    @Test
    void onlyKnownLegacyObjectPaddingIsClearedWithoutChangingRecordOffsets() {
        byte[] book = new byte[4 + 34 + 4];
        write16(book, 0, 0x005D); write16(book, 2, 34);
        write16(book, 4, 0x0015); write16(book, 6, 18);
        write16(book, 26, 0); write16(book, 28, 0);
        System.arraycopy(LEGACY_PADDING, 0, book, 30, 8);
        write16(book, 38, 0x000A); write16(book, 40, 0);

        assertThat(NonghyupXlsCompatibility.repairLegacyObjPadding(book)).isEqualTo(1);
        assertThat(Arrays.copyOfRange(book, 30, 38)).containsOnly((byte) 0);
        assertThat(book[38]).isEqualTo((byte) 0x0A);
        assertThat(NonghyupXlsCompatibility.repairLegacyObjPadding(book)).isZero();
    }

    @Test
    void unknownObjectPaddingIsNeverSilentlyAltered() {
        byte[] record = new byte[38];
        write16(record, 0, 0x005D);write16(record, 2, 34);
        write16(record, 4, 0x0015);write16(record, 6, 18);
        System.arraycopy(LEGACY_PADDING, 0, record, 30, 8);
        record[33] = 9;
        byte[] original = record.clone();

        assertThat(NonghyupXlsCompatibility.repairLegacyObjPadding(record)).isZero();
        assertThat(record).containsExactly(original);
    }

    @Test
    void generatedXlsWithMalformedNonghyupObjectCanStillBeOpened() throws Exception {
        byte[] original;
        try (HSSFWorkbook book = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("입출금거래내역");
            sheet.createRow(0).createCell(0).setCellValue("계좌번호");
            sheet.createRow(1).createCell(0).setCellValue("거래일시");
            var patriarch = sheet.createDrawingPatriarch();
            var anchor = new HSSFClientAnchor();
            anchor.setCol1(2);anchor.setCol2(3);anchor.setRow1(3);anchor.setRow2(4);
            HSSFSimpleShape shape = patriarch.createSimpleShape(anchor);
            shape.setShapeType(HSSFSimpleShape.OBJECT_TYPE_RECTANGLE);
            book.write(out);
            original = out.toByteArray();
        }

        byte[] corrupted;
        try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(original))) {
            String streamName = fs.getRoot().hasEntry("Workbook") ? "Workbook" : "Book";
            byte[] biff;
            try (var stream = fs.createDocumentInputStream(streamName)) {
                biff = stream.readAllBytes();
            }
            ByteArrayOutputStream amended = new ByteArrayOutputStream(biff.length + 16);
            boolean injected = false;
            for (int position = 0; position + 4 <= biff.length;) {
                int sid = u16(biff, position);
                int length = u16(biff, position + 2);
                int end = position + 4 + length;
                if (end > biff.length) throw new IllegalStateException("Malformed test workbook");
                if (!injected && sid == 0x005D && length == 26
                        && u16(biff, position + 4) == 0x15
                        && u16(biff, position + 6) == 18
                        && u16(biff, position + 26) == 0
                        && u16(biff, position + 28) == 0) {
                    byte[] fragment = Arrays.copyOfRange(biff, position, end);
                    write16(fragment, 2, 34);
                    amended.write(fragment);
                    amended.write(LEGACY_PADDING);
                    injected = true;
                } else amended.write(biff, position, end - position);
                position = end;
            }
            assertThat(injected).as("Synthetic drawing should contain an OBJ subrecord").isTrue();
            fs.createOrUpdateDocument(new ByteArrayInputStream(amended.toByteArray()), streamName);
            ByteArrayOutputStream ole = new ByteArrayOutputStream();
            fs.writeFilesystem(ole);
            corrupted = ole.toByteArray();
        }

        try (Workbook fixed = NonghyupXlsCompatibility.openWorkbook(corrupted)) {
            assertThat(fixed.getSheetAt(0).getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("계좌번호");
        }
    }

    private static int u16(byte[] data, int offset) {
        return (data[offset] & 0xff) | (data[offset+1] & 0xff) << 8;
    }
    private static void write16(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset+1] = (byte) (value >>> 8);
    }
}
