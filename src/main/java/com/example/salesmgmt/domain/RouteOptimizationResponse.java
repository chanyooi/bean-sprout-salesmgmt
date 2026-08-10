package com.example.salesmgmt.domain;

import java.util.List;

public record RouteOptimizationResponse(
        boolean available,
        String message,
        String routeLabel,
        long currentDistanceMeters,
        long suggestedDistanceMeters,
        long savingsMeters,
        List<Long> vendorIds,
        List<Stop> stops
) {
    public record Stop(
            Long vendorId,
            String name,
            int order
    ) {}
}
