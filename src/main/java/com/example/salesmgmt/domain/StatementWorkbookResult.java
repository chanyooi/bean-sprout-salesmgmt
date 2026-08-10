package com.example.salesmgmt.domain;

public record StatementWorkbookResult(
        byte[] fileBytes,
        String filename,
        int generatedSheetCount,
        int sheetWithSalesCount,
        int removedEmptySheetCount,
        int warningCount
) {
}
