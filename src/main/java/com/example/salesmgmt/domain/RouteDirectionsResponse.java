package com.example.salesmgmt.domain;

import java.util.List;

public record RouteDirectionsResponse(
        boolean enabled,
        String message,
        long distanceMeters,
        long durationMillis,
        List<Point> path
) {
    public record Point(double lat, double lng) {}
}
