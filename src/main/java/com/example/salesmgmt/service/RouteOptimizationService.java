package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RouteOptimizationService {

    private final VendorManagementService vendorManagementService;
    private final NaverDirectionsService naverDirectionsService;

    public RouteOptimizationService(
            VendorManagementService vendorManagementService,
            NaverDirectionsService naverDirectionsService
    ) {
        this.vendorManagementService = vendorManagementService;
        this.naverDirectionsService = naverDirectionsService;
    }

    /**
     * 현재 1번 거래처를 출발점으로 고정합니다.
     * 나머지는 직선거리 기반 nearest-neighbor + 2-opt로 순서를 개선한 뒤,
     * 현재/추천 순서 모두 NAVER Directions 실제 도로거리로 검증합니다.
     *
     * 전역 최적해를 수학적으로 보장하는 TSP solver는 아니지만,
     * API 수백 회를 쓰지 않고 실무에서 빠르게 쓸 수 있는 휴리스틱입니다.
     */
    public RouteOptimizationResponse optimize(RouteCode routeCode) {
        if (routeCode == null || routeCode == RouteCode.NONE) {
            return unavailable(
                    routeCode,
                    "A코스/B코스/김천코스 중 하나를 선택해주세요."
            );
        }

        List<VendorManagementRow> allRouteRows =
                vendorManagementService.findAllRows()
                        .stream()
                        .filter(row -> row.routeCode() == routeCode)
                        .sorted(Comparator.comparing(
                                VendorManagementRow::routeOrder,
                                Comparator.nullsLast(Integer::compareTo)
                        ))
                        .toList();

        List<VendorManagementRow> activeRows =
                allRouteRows.stream()
                        .filter(VendorManagementRow::active)
                        .toList();

        if (activeRows.size() < 3) {
            return unavailable(
                    routeCode,
                    "최적순서 추천은 활성 거래처가 3곳 이상일 때 사용할 수 있습니다."
            );
        }

        List<VendorManagementRow> missingLocation =
                activeRows.stream()
                        .filter(row -> !row.hasLocation())
                        .toList();

        if (!missingLocation.isEmpty()) {
            String names = missingLocation.stream()
                    .limit(4)
                    .map(VendorManagementRow::vendorName)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");

            return unavailable(
                    routeCode,
                    "위치가 없는 활성 거래처가 있습니다: "
                            + names
                            + (missingLocation.size() > 4
                                    ? " 외 " + (missingLocation.size() - 4) + "곳"
                                    : "")
            );
        }

        VendorManagementRow start = activeRows.getFirst();

        List<VendorManagementRow> candidate =
                nearestNeighbor(start, activeRows);

        candidate = twoOpt(candidate);

        // 비활성 거래처는 원래 상대순서를 유지한 채 뒤에 붙입니다.
        List<Long> finalVendorIds =
                new ArrayList<>(
                        candidate.stream()
                                .map(VendorManagementRow::vendorId)
                                .toList()
                );

        allRouteRows.stream()
                .filter(row -> !row.active())
                .map(VendorManagementRow::vendorId)
                .forEach(finalVendorIds::add);

        RouteDirectionsResponse currentRoad =
                naverDirectionsService.findRoute(routeCode);

        RouteDirectionsResponse suggestedRoad =
                naverDirectionsService.findRouteForVendorIds(
                        routeCode,
                        candidate.stream()
                                .map(VendorManagementRow::vendorId)
                                .toList()
                );

        long currentDistance =
                currentRoad.enabled()
                        && currentRoad.distanceMeters() > 0
                        ? currentRoad.distanceMeters()
                        : Math.round(pathDistance(activeRows));

        long suggestedDistance =
                suggestedRoad.enabled()
                        && suggestedRoad.distanceMeters() > 0
                        ? suggestedRoad.distanceMeters()
                        : Math.round(pathDistance(candidate));

        long savings =
                currentDistance - suggestedDistance;

        List<RouteOptimizationResponse.Stop> stops =
                new ArrayList<>();

        int order = 1;
        for (VendorManagementRow row : candidate) {
            stops.add(new RouteOptimizationResponse.Stop(
                    row.vendorId(),
                    row.vendorName(),
                    order++
            ));
        }

        String message;
        if (savings > 0) {
            message =
                    "현재 1번 거래처를 출발점으로 고정한 추천순서입니다. "
                    + "적용 전 목록을 확인해주세요.";
        } else {
            message =
                    "추천 계산 결과 현재 순서보다 실제 도로거리가 짧아지지 않았습니다. "
                    + "현재 순서를 유지하는 편이 좋습니다.";
        }

        return new RouteOptimizationResponse(
                true,
                message,
                routeCode.getLabel(),
                currentDistance,
                suggestedDistance,
                savings,
                List.copyOf(finalVendorIds),
                List.copyOf(stops)
        );
    }

    private List<VendorManagementRow> nearestNeighbor(
            VendorManagementRow start,
            List<VendorManagementRow> all
    ) {
        List<VendorManagementRow> result =
                new ArrayList<>();
        result.add(start);

        Set<Long> visited = new HashSet<>();
        visited.add(start.vendorId());

        VendorManagementRow current = start;

        while (result.size() < all.size()) {
            final VendorManagementRow from = current;

            VendorManagementRow next =
                    all.stream()
                            .filter(row -> !visited.contains(
                                    row.vendorId()
                            ))
                            .min(Comparator.comparingDouble(
                                    row -> distance(
                                            from,
                                            row
                                    )
                            ))
                            .orElseThrow();

            result.add(next);
            visited.add(next.vendorId());
            current = next;
        }

        return result;
    }

    private List<VendorManagementRow> twoOpt(
            List<VendorManagementRow> initial
    ) {
        List<VendorManagementRow> best =
                new ArrayList<>(initial);

        boolean improved = true;
        int pass = 0;

        while (improved && pass++ < 30) {
            improved = false;

            // index 0은 출발점으로 고정
            for (int i = 1; i < best.size() - 1; i++) {
                for (int k = i + 1; k < best.size(); k++) {
                    double before =
                            pathDistance(best);

                    List<VendorManagementRow> candidate =
                            twoOptSwap(best, i, k);

                    double after =
                            pathDistance(candidate);

                    if (after + 1.0 < before) {
                        best = candidate;
                        improved = true;
                    }
                }
            }
        }

        return best;
    }

    private List<VendorManagementRow> twoOptSwap(
            List<VendorManagementRow> route,
            int i,
            int k
    ) {
        List<VendorManagementRow> result =
                new ArrayList<>(route.size());

        result.addAll(route.subList(0, i));

        List<VendorManagementRow> reversed =
                new ArrayList<>(route.subList(i, k + 1));
        Collections.reverse(reversed);
        result.addAll(reversed);

        if (k + 1 < route.size()) {
            result.addAll(route.subList(
                    k + 1,
                    route.size()
            ));
        }

        return result;
    }

    private double pathDistance(
            List<VendorManagementRow> rows
    ) {
        double total = 0;

        for (int i = 1; i < rows.size(); i++) {
            total += distance(
                    rows.get(i - 1),
                    rows.get(i)
            );
        }

        return total;
    }

    private double distance(
            VendorManagementRow a,
            VendorManagementRow b
    ) {
        final double earth = 6_371_000.0;

        double lat1 = Math.toRadians(
                a.latitude().doubleValue()
        );
        double lat2 = Math.toRadians(
                b.latitude().doubleValue()
        );
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(
                b.longitude().doubleValue()
                        - a.longitude().doubleValue()
        );

        double h =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(lat1)
                        * Math.cos(lat2)
                        * Math.sin(dLng / 2)
                        * Math.sin(dLng / 2);

        return earth * 2
                * Math.atan2(
                        Math.sqrt(h),
                        Math.sqrt(1 - h)
                );
    }

    private RouteOptimizationResponse unavailable(
            RouteCode routeCode,
            String message
    ) {
        return new RouteOptimizationResponse(
                false,
                message,
                routeCode == null
                        ? ""
                        : routeCode.getLabel(),
                0,
                0,
                0,
                List.of(),
                List.of()
        );
    }
}
