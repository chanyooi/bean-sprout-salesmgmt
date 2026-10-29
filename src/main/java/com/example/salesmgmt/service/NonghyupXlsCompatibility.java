package com.example.salesmgmt.service;

import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Narrow compatibility repair for legacy Nonghyup BIFF8 exports.
 *
 * Some bank-generated spreadsheets have an OBJ (0x005D) record containing
 * a valid CommonObjectData and End subrecord, followed by the non-zero
 * 8-byte markers [07 00 00 00 08 00 00 00]. POI rejects the padding
 * with "Leftover 8 bytes in subrecord data", although the cell data is intact.
 *
 * Only this exact, recognized decoration is zeroed in an in-memory copy.
 * Record lengths and all sheet offsets remain untouched; uploaded bytes
 * are never written back to the source file.
 */
public final class NonghyupXlsCompatibility {
    private static final byte[] OLE_MAGIC = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };
    private static final byte[] BAD_PADDING = {7, 0, 0, 0, 8, 0, 0, 0};

    private NonghyupXlsCompatibility() { }

    public static Workbook openWorkbook(byte[] uploaded) throws IOException {
        if (!isOle2(uploaded)) {
            return WorkbookFactory.create(new ByteArrayInputStream(uploaded));
        }

        try (POIFSFileSystem filesystem = new POIFSFileSystem(new ByteArrayInputStream(uploaded))) {
            var root = filesystem.getRoot();
            String workbookName;
            if (root.hasEntry("Workbook")) workbookName = "Workbook";
            else if (root.hasEntry("Book")) workbookName = "Book";
            else return WorkbookFactory.create(new ByteArrayInputStream(uploaded));

            byte[] biff;
            try (DocumentInputStream document = filesystem.createDocumentInputStream(workbookName)) {
                biff = document.readAllBytes();
            }
            int corrected = repairLegacyObjPadding(biff);
            if (corrected == 0) {
                return WorkbookFactory.create(new ByteArrayInputStream(uploaded));
            }

            filesystem.createOrUpdateDocument(new ByteArrayInputStream(biff), workbookName);
            ByteArrayOutputStream updatedOle = new ByteArrayOutputStream(uploaded.length + 1024);
            filesystem.writeFilesystem(updatedOle);
            return WorkbookFactory.create(new ByteArrayInputStream(updatedOle.toByteArray()));
        }
    }

    public static int repairLegacyObjPadding(byte[] biff) {
        int corrected = 0;
        int offset = 0;
        while (offset + 4 <= biff.length) {
            int sid = u16(biff, offset);
            int size = u16(biff, offset + 2);
            int end = offset + 4 + size;
            if (end > biff.length || end < offset) break;

            if (sid == 0x005D && size == 34
                    && u16(biff, offset + 4) == 0x0015
                    && u16(biff, offset + 6) == 18
                    && u16(biff, offset + 26) == 0
                    && u16(biff, offset + 28) == 0
                    && endsWith(biff, end - BAD_PADDING.length, BAD_PADDING)) {
                Arrays.fill(biff, end - BAD_PADDING.length, end, (byte) 0);
                corrected++;
            }
            offset = end;
        }
        return corrected;
    }

    private static boolean isOle2(byte[] bytes) {
        if (bytes == null || bytes.length < OLE_MAGIC.length) return false;
        for (int i = 0; i < OLE_MAGIC.length; i++) {
            if (bytes[i] != OLE_MAGIC[i]) return false;
        }
        return true;
    }

    private static boolean endsWith(byte[] data, int offset, byte[] suffix) {
        if (offset < 0 || offset + suffix.length > data.length) return false;
        for (int i = 0; i < suffix.length; i++) {
            if (data[offset + i] != suffix[i]) return false;
        }
        return true;
    }

    private static int u16(byte[] bytes, int at) {
        return (bytes[at] & 0xff) | ((bytes[at + 1] & 0xff) << 8);
    }
}
