package com.example.salesmgmt.domain;

import java.math.BigDecimal;

public record PriceImportRow(
        String inputVendor,
        String statementVendor,
        String itemName,
        BigDecimal unitPrice,
        String sourceSheet,
        String sourceCell,
        String originalLabel
) {
}
