package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.RouteCode;
import com.example.salesmgmt.domain.RouteDirectionsResponse;
import com.example.salesmgmt.domain.VendorManagementRow;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NaverDirectionsService {

    private static final String DEFAULT_CLIENT_ID = "iwii3ygty2";
    private static final String BASE =
            "https://maps.apigw.ntruss.com/map-direction-15/v1/driving";

    private final VendorManagementService vendorManagementService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, CacheEntry> cache =
            new ConcurrentHashMap<>();

    public NaverDirectionsService(
            VendorManagementService vendorManagementService,
            ObjectMapper objectMapper
    ) {
        this.vendorManagementService = vendorManagementService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public RouteDirectionsResponse findRoute(RouteCode routeCode) {
        List<VendorManagementRow> stops =
                currentStops(routeCode);

        return buildRouteResponse(
                routeCode,
                stops
        );
    }

    public RouteDirectionsResponse findRouteForVendorIds(
            RouteCode routeCode,
            List<Long> orderedVendorIds
    ) {
        if (orderedVendorIds == null
                || orderedVendorIds.isEmpty()) {
            return findRoute(routeCode);
        }

        Map<Long, VendorManagementRow> byId =
                vendorManagementService.findAllRows()
                        .stream()
                        .filter(VendorManagementRow::active)
                        .filter(row -> row.routeCode() == routeCode)
                        .filter(VendorManagementRow::hasLocation)
                        .collect(java.util.stream.Collectors.toMap(
                                VendorManagementRow::vendorId,
                                row -> row
                        ));

        List<VendorManagementRow> stops =
                new ArrayList<>();

        for (Long id : orderedVendorIds) {
            VendorManagementRow row = byId.get(id);
            if (row != null) {
                stops.add(row);
            }
        }

        return buildRouteResponse(
                routeCode,
                stops
        );
    }

    private List<VendorManagementRow> currentStops(
            RouteCode routeCode
    ) {
        return vendorManagementService.findAllRows()
                .stream()
                .filter(VendorManagementRow::active)
                .filter(row -> row.routeCode() == routeCode)
                .filter(VendorManagementRow::hasLocation)
                .sorted(Comparator.comparing(
                        VendorManagementRow::routeOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();
    }

    private RouteDirectionsResponse buildRouteResponse(
            RouteCode routeCode,
            List<VendorManagementRow> stops
    ) {
        String secret =
                System.getenv("NAVER_MAP_CLIENT_SECRET");

        String clientId = Optional.ofNullable(
                System.getenv("NAVER_MAP_CLIENT_ID")
        ).filter(value -> !value.isBlank())
         .orElse(DEFAULT_CLIENT_ID);

        if (secret == null || secret.isBlank()) {
            return new RouteDirectionsResponse(
                    false,
                    "NAVER_MAP_CLIENT_SECRET 환경변수가 없어 실제 도로경로는 아직 비활성화되어 있습니다.",
                    0,
                    0,
                    List.of()
            );
        }

        if (stops.size() < 2) {
            return new RouteDirectionsResponse(
                    true,
                    "경로를 그리려면 좌표가 저장된 거래처가 2곳 이상 필요합니다.",
                    0,
                    0,
                    List.of()
            );
        }

        String cacheKey =
                buildCacheKey(routeCode, stops);

        CacheEntry cached = cache.get(cacheKey);

        if (cached != null
                && cached.createdAtMillis()
                > System.currentTimeMillis()
                - 10 * 60 * 1000L) {
            return cached.response();
        }

        List<RouteDirectionsResponse.Point> fullPath =
                new ArrayList<>();

        long totalDistance = 0;
        long totalDuration = 0;

        // Directions 15: 경유지 최대 15개.
        // start + waypoints 15 + goal = 한 요청에 최대 17개 정류장.
        int startIndex = 0;

        while (startIndex < stops.size() - 1) {
            int endIndex = Math.min(
                    startIndex + 16,
                    stops.size() - 1
            );

            List<VendorManagementRow> chunk =
                    stops.subList(
                            startIndex,
                            endIndex + 1
                    );

            SegmentResult segment =
                    requestSegment(
                            clientId,
                            secret,
                            chunk
                    );

            totalDistance +=
                    segment.distanceMeters();

            totalDuration +=
                    segment.durationMillis();

            if (!fullPath.isEmpty()
                    && !segment.path().isEmpty()) {
                fullPath.addAll(
                        segment.path().subList(
                                1,
                                segment.path().size()
                        )
                );
            } else {
                fullPath.addAll(segment.path());
            }

            startIndex = endIndex;
        }

        RouteDirectionsResponse response =
                new RouteDirectionsResponse(
                        true,
                        "실제 도로경로",
                        totalDistance,
                        totalDuration,
                        List.copyOf(fullPath)
                );

        cache.put(
                cacheKey,
                new CacheEntry(
                        System.currentTimeMillis(),
                        response
                )
        );

        return response;
    }

    private SegmentResult requestSegment(
            String clientId,
            String secret,
            List<VendorManagementRow> stops
    ) {
        VendorManagementRow start = stops.getFirst();
        VendorManagementRow goal = stops.getLast();

        StringBuilder url = new StringBuilder(BASE)
                .append("?start=")
                .append(enc(coord(start)))
                .append("&goal=")
                .append(enc(coord(goal)))
                .append("&option=traoptimal");

        if (stops.size() > 2) {
            String waypoints = stops.subList(
                    1,
                    stops.size() - 1
            ).stream()
             .map(this::coord)
             .reduce((a, b) -> a + "|" + b)
             .orElse("");

            url.append("&waypoints=")
                    .append(enc(waypoints));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(15))
                .header(
                        "x-ncp-apigw-api-key-id",
                        clientId
                )
                .header(
                        "x-ncp-apigw-api-key",
                        secret
                )
                .GET()
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "네이버 Directions 응답 오류: "
                                + response.statusCode()
                );
            }

            JsonNode root =
                    objectMapper.readTree(response.body());

            JsonNode routeArray =
                    root.path("route").path("traoptimal");

            if (!routeArray.isArray()
                    || routeArray.isEmpty()) {
                throw new IllegalStateException(
                        "네이버에서 도로경로를 찾지 못했습니다."
                );
            }

            JsonNode route = routeArray.get(0);
            long distance =
                    route.path("summary")
                            .path("distance")
                            .asLong(0);
            long duration =
                    route.path("summary")
                            .path("duration")
                            .asLong(0);

            List<RouteDirectionsResponse.Point> path =
                    new ArrayList<>();

            for (JsonNode point : route.path("path")) {
                if (point.isArray()
                        && point.size() >= 2) {
                    path.add(
                            new RouteDirectionsResponse.Point(
                                    point.get(1).asDouble(),
                                    point.get(0).asDouble()
                            )
                    );
                }
            }

            return new SegmentResult(
                    distance,
                    duration,
                    List.copyOf(path)
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "네이버 실제 도로경로 조회에 실패했습니다: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private String coord(VendorManagementRow row) {
        return row.longitude().toPlainString()
                + ","
                + row.latitude().toPlainString();
    }

    private String buildCacheKey(
            RouteCode route,
            List<VendorManagementRow> rows
    ) {
        StringBuilder key = new StringBuilder(
                route.name()
        );

        for (VendorManagementRow row : rows) {
            key.append("|")
                    .append(row.vendorId())
                    .append(":")
                    .append(row.latitude())
                    .append(",")
                    .append(row.longitude());
        }

        return key.toString();
    }

    private String enc(String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    private record SegmentResult(
            long distanceMeters,
            long durationMillis,
            List<RouteDirectionsResponse.Point> path
    ) {}

    private record CacheEntry(
            long createdAtMillis,
            RouteDirectionsResponse response
    ) {}
}
