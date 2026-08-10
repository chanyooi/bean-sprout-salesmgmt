package com.example.salesmgmt.domain;

public record SaveResult(
        int createdVendors,
        int createdOrders,
        int savedItems,
        int updatedItems,
        int deletedItems,
        int deletedOrders,
        int skippedDuplicateItems
) {
}
