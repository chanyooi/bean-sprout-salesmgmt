package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.PaymentCycle;
import com.example.salesmgmt.domain.RouteCode;
import com.example.salesmgmt.domain.VendorManagementRow;
import com.example.salesmgmt.domain.VendorRouteSummary;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.entity.VendorPriceEntity;
import com.example.salesmgmt.entity.VendorProfileEntity;
import com.example.salesmgmt.repository.VendorPriceRepository;
import com.example.salesmgmt.repository.VendorProfileRepository;
import com.example.salesmgmt.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class VendorManagementService {

    private final VendorRepository vendorRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final VendorPriceRepository vendorPriceRepository;

    public VendorManagementService(
            VendorRepository vendorRepository,
            VendorProfileRepository vendorProfileRepository,
            VendorPriceRepository vendorPriceRepository
    ) {
        this.vendorRepository = vendorRepository;
        this.vendorProfileRepository = vendorProfileRepository;
        this.vendorPriceRepository = vendorPriceRepository;
    }

    @Transactional(readOnly = true)
    public List<VendorManagementRow> findAllRows() {
        Map<Long, VendorProfileEntity> profiles = new HashMap<>();
        for (VendorProfileEntity profile : vendorProfileRepository.findAll()) {
            profiles.put(profile.getVendor().getId(), profile);
        }

        Map<Long, BigDecimal> returnContainerPrices = new HashMap<>();
        for (VendorPriceEntity price : vendorPriceRepository.findAllWithVendor()) {
            if ("회수통".equals(price.getItemName())
                    && price.getUnitPrice() != null
                    && price.getUnitPrice().signum() > 0) {
                returnContainerPrices.put(
                        price.getVendor().getId(),
                        price.getUnitPrice()
                );
            }
        }

        return vendorRepository.findAll()
                .stream()
                .map(vendor -> toRow(
                        vendor,
                        profiles.get(vendor.getId()),
                        returnContainerPrices.get(vendor.getId())
                ))
                .sorted(Comparator
                        .comparing((VendorManagementRow row) -> !row.active())
                        .thenComparing(row -> routeSort(row.routeCode()))
                        .thenComparing(
                                VendorManagementRow::routeOrder,
                                Comparator.nullsLast(Integer::compareTo)
                        )
                        .thenComparing(VendorManagementRow::vendorName))
                .toList();
    }

    @Transactional(readOnly = true)
    public VendorRouteSummary getRouteSummary() {
        List<VendorManagementRow> rows = findAllRows();

        long active = rows.stream()
                .filter(VendorManagementRow::active)
                .count();

        long routeA = rows.stream()
                .filter(VendorManagementRow::active)
                .filter(row -> row.routeCode() == RouteCode.A)
                .count();

        long routeB = rows.stream()
                .filter(VendorManagementRow::active)
                .filter(row -> row.routeCode() == RouteCode.B)
                .count();

        long kimcheon = rows.stream()
                .filter(VendorManagementRow::active)
                .filter(row -> row.routeCode() == RouteCode.KIMCHEON)
                .count();

        long unassigned = rows.stream()
                .filter(VendorManagementRow::active)
                .filter(row -> row.routeCode() == RouteCode.NONE)
                .count();

        return new VendorRouteSummary(
                active,
                routeA,
                routeB,
                kimcheon,
                unassigned
        );
    }

    /**
     * 거래처 기본정보를 저장합니다.
     *
     * 방문순서는 사용자가 숫자를 직접 관리하지 않습니다.
     * - 기존 코스를 그대로 저장: 기존 순서 유지
     * - 새 코스에 처음 배정: 해당 코스 맨 뒤에 자동 추가
     * - 다른 코스로 이동: 새 코스 맨 뒤에 자동 추가
     * - 미지정으로 변경: 순서 제거
     *
     * 실제 중간 삽입/이동은 reorderRoute()에서 전체 번호를 1부터 다시 매깁니다.
     */
    @Transactional
    public void updateProfile(
            Long vendorId,
            boolean active,
            RouteCode routeCode,
            Integer ignoredRouteOrder,
            String address,
            String phone,
            PaymentCycle paymentCycle,
            String memo,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        VendorEntity vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "거래처를 찾을 수 없습니다."
                ));

        RouteCode safeRoute = routeCode == null
                ? RouteCode.NONE
                : routeCode;

        validateCoordinates(latitude, longitude);

        VendorProfileEntity profile = vendorProfileRepository
                .findByVendor_Id(vendorId)
                .orElseGet(() -> new VendorProfileEntity(vendor));

        RouteCode previousRoute = profile.getRouteCode();
        Integer previousOrder = profile.getRouteOrder();

        Integer effectiveOrder;

        if (safeRoute == RouteCode.NONE) {
            effectiveOrder = null;
        } else if (safeRoute == previousRoute
                && previousOrder != null
                && previousOrder > 0) {
            effectiveOrder = previousOrder;
        } else {
            // 새 코스 또는 다른 코스로 이동하면 일단 맨 뒤에 배치합니다.
            effectiveOrder = nextRouteOrder(safeRoute);
        }

        profile.update(
                active,
                safeRoute,
                effectiveOrder,
                address,
                phone,
                paymentCycle,
                memo,
                latitude,
                longitude
        );

        vendorProfileRepository.save(profile);
        vendorProfileRepository.flush();

        // 빠져나간 코스의 빈 번호를 즉시 당깁니다.
        if (previousRoute != null
                && previousRoute != RouteCode.NONE
                && previousRoute != safeRoute) {
            normalizeRoute(previousRoute);
        }

        // 새 코스도 혹시 과거 수동 입력으로 중복/빈 번호가 있다면 정리합니다.
        if (safeRoute != RouteCode.NONE) {
            normalizeRoute(safeRoute);
        }
    }

    /**
     * 드래그 화면에서 전달된 순서대로 코스 전체를 1,2,3... 재정렬합니다.
     *
     * 전달 목록에 빠진 거래처가 있어도 삭제하지 않고 기존 순서 뒤에 붙입니다.
     */
    @Transactional
    public void reorderRoute(
            RouteCode routeCode,
            List<Long> orderedVendorIds
    ) {
        if (routeCode == null || routeCode == RouteCode.NONE) {
            throw new IllegalArgumentException(
                    "A코스, B코스, 김천코스 중 하나를 선택해주세요."
            );
        }

        List<VendorProfileEntity> routeProfiles =
                profilesForRoute(routeCode);

        if (routeProfiles.isEmpty()) {
            return;
        }

        Map<Long, VendorProfileEntity> byVendorId = new HashMap<>();
        for (VendorProfileEntity profile : routeProfiles) {
            byVendorId.put(
                    profile.getVendor().getId(),
                    profile
            );
        }

        // 중복 ID 제거 + 실제 해당 코스 거래처만 허용
        LinkedHashSet<Long> requestedIds = new LinkedHashSet<>();
        if (orderedVendorIds != null) {
            for (Long vendorId : orderedVendorIds) {
                if (vendorId == null) {
                    continue;
                }

                if (!byVendorId.containsKey(vendorId)) {
                    throw new IllegalArgumentException(
                            "현재 " + routeCode.getLabel()
                                    + "에 속하지 않은 거래처가 포함되어 있습니다."
                    );
                }

                requestedIds.add(vendorId);
            }
        }

        List<VendorProfileEntity> reordered = new ArrayList<>();

        for (Long vendorId : requestedIds) {
            reordered.add(byVendorId.get(vendorId));
        }

        // 화면 목록에서 빠진 거래처는 기존 순서대로 뒤에 보존합니다.
        Set<Long> included = new HashSet<>(requestedIds);

        routeProfiles.stream()
                .filter(profile -> !included.contains(
                        profile.getVendor().getId()
                ))
                .sorted(routeProfileComparator())
                .forEach(reordered::add);

        int order = 1;
        for (VendorProfileEntity profile : reordered) {
            applyRouteOrder(
                    profile,
                    routeCode,
                    order++
            );
        }

        vendorProfileRepository.saveAll(reordered);
    }

    private int nextRouteOrder(RouteCode routeCode) {
        return profilesForRoute(routeCode)
                .stream()
                .map(VendorProfileEntity::getRouteOrder)
                .filter(order -> order != null && order > 0)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private void normalizeRoute(RouteCode routeCode) {
        if (routeCode == null || routeCode == RouteCode.NONE) {
            return;
        }

        List<VendorProfileEntity> profiles =
                profilesForRoute(routeCode)
                        .stream()
                        .sorted(routeProfileComparator())
                        .toList();

        int order = 1;
        for (VendorProfileEntity profile : profiles) {
            applyRouteOrder(profile, routeCode, order++);
        }

        vendorProfileRepository.saveAll(profiles);
    }

    private List<VendorProfileEntity> profilesForRoute(
            RouteCode routeCode
    ) {
        return vendorProfileRepository.findAll()
                .stream()
                .filter(profile -> profile.getRouteCode() == routeCode)
                .toList();
    }

    private Comparator<VendorProfileEntity> routeProfileComparator() {
        return Comparator
                .comparing(
                        VendorProfileEntity::getRouteOrder,
                        Comparator.nullsLast(Integer::compareTo)
                )
                .thenComparing(
                        profile -> profile.getVendor().getInputName()
                );
    }

    private void applyRouteOrder(
            VendorProfileEntity profile,
            RouteCode routeCode,
            int routeOrder
    ) {
        profile.update(
                profile.isActive(),
                routeCode,
                routeOrder,
                profile.getAddress(),
                profile.getPhone(),
                profile.getPaymentCycle(),
                profile.getMemo(),
                profile.getLatitude(),
                profile.getLongitude()
        );
    }

    private void validateCoordinates(
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException(
                    "위도와 경도는 둘 다 입력하거나 둘 다 비워주세요."
            );
        }

        if (latitude == null) {
            return;
        }

        if (latitude.compareTo(new BigDecimal("-90")) < 0
                || latitude.compareTo(new BigDecimal("90")) > 0) {
            throw new IllegalArgumentException(
                    "위도는 -90~90 범위여야 합니다."
            );
        }

        if (longitude.compareTo(new BigDecimal("-180")) < 0
                || longitude.compareTo(new BigDecimal("180")) > 0) {
            throw new IllegalArgumentException(
                    "경도는 -180~180 범위여야 합니다."
            );
        }
    }

    private VendorManagementRow toRow(
            VendorEntity vendor,
            VendorProfileEntity profile,
            BigDecimal returnContainerPrice
    ) {
        if (profile == null) {
            return new VendorManagementRow(
                    vendor.getId(),
                    vendor.getInputName(),
                    vendor.getStatementName(),
                    true,
                    RouteCode.NONE,
                    null,
                    null,
                    null,
                    PaymentCycle.MONTHLY,
                    null,
                    null,
                    null,
                    returnContainerPrice
            );
        }

        return new VendorManagementRow(
                vendor.getId(),
                vendor.getInputName(),
                vendor.getStatementName(),
                profile.isActive(),
                profile.getRouteCode(),
                profile.getRouteOrder(),
                profile.getAddress(),
                profile.getPhone(),
                profile.getPaymentCycle(),
                profile.getMemo(),
                profile.getLatitude(),
                profile.getLongitude(),
                returnContainerPrice
        );
    }

    private int routeSort(RouteCode routeCode) {
        return switch (routeCode) {
            case A -> 0;
            case B -> 1;
            case KIMCHEON -> 2;
            case NONE -> 3;
        };
    }
}
