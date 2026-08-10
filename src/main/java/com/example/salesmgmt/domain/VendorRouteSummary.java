package com.example.salesmgmt.domain;

public record VendorRouteSummary(
        long activeVendorCount,
        long routeACount,
        long routeBCount,
        long kimcheonRouteCount,
        long unassignedCount
) {
}
