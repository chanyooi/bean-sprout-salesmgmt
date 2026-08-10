package com.example.salesmgmt.domain;

public record PriceSaveResult(
        int createdVendors,
        int createdPrices,
        int updatedPrices,
        int unchangedPrices,
        int appliedSalesItems
) {
}
