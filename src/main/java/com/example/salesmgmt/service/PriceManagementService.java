package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.ItemCatalog;
import com.example.salesmgmt.domain.PriceImportRow;
import com.example.salesmgmt.domain.PriceSaveResult;
import com.example.salesmgmt.domain.PriceViewRow;
import com.example.salesmgmt.domain.VendorOption;
import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.entity.VendorPriceEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import com.example.salesmgmt.repository.VendorPriceRepository;
import com.example.salesmgmt.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PriceManagementService {

    private final VendorRepository vendorRepository;
    private final VendorPriceRepository vendorPriceRepository;
    private final SalesItemRepository salesItemRepository;

    public PriceManagementService(
            VendorRepository vendorRepository,
            VendorPriceRepository vendorPriceRepository,
            SalesItemRepository salesItemRepository
    ) {
        this.vendorRepository = vendorRepository;
        this.vendorPriceRepository = vendorPriceRepository;
        this.salesItemRepository = salesItemRepository;
    }

    @Transactional
    public PriceSaveResult saveImportedPrices(List<PriceImportRow> rows) {
        int createdVendors = 0;
        int createdPrices = 0;
        int updatedPrices = 0;
        int unchangedPrices = 0;
        Map<String, VendorEntity> vendorCache = new HashMap<>();

        for (PriceImportRow row : rows) {
            VendorEntity vendor = vendorCache.get(row.inputVendor());
            if (vendor == null) {
                var existingVendor = vendorRepository.findByInputName(row.inputVendor());
                if (existingVendor.isPresent()) {
                    vendor = existingVendor.get();
                    vendor.updateStatementSettings(row.statementVendor(), true);
                } else {
                    vendor = vendorRepository.save(new VendorEntity(
                            row.inputVendor(), row.statementVendor(), true
                    ));
                    createdVendors++;
                }
                vendorCache.put(row.inputVendor(), vendor);
            }

            var existingPrice = vendorPriceRepository.findByVendor_IdAndItemName(
                    vendor.getId(), row.itemName()
            );
            BigDecimal normalizedPrice = normalizePrice(row.unitPrice());

            if (existingPrice.isEmpty()) {
                vendorPriceRepository.save(new VendorPriceEntity(
                        vendor,
                        row.itemName(),
                        normalizedPrice,
                        row.sourceSheet()
                ));
                createdPrices++;
            } else if (existingPrice.get().getUnitPrice().compareTo(normalizedPrice) == 0) {
                unchangedPrices++;
            } else {
                existingPrice.get().update(normalizedPrice, row.sourceSheet());
                updatedPrices++;
            }
        }

        int appliedSalesItems = applyPricesToUnpricedSales();
        return new PriceSaveResult(
                createdVendors,
                createdPrices,
                updatedPrices,
                unchangedPrices,
                appliedSalesItems
        );
    }

    @Transactional(readOnly = true)
    public List<VendorOption> findVendors() {
        return vendorRepository.findAllByOrderByInputNameAsc()
                .stream()
                .map(vendor -> new VendorOption(
                        vendor.getId(), vendor.getInputName(), vendor.getStatementName()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PriceViewRow> findPrices(Long vendorId) {
        if (vendorId == null) return List.of();
        return vendorPriceRepository.findByVendor_IdOrderByItemNameAsc(vendorId)
                .stream()
                .map(price -> new PriceViewRow(
                        price.getId(), price.getItemName(), price.getUnitPrice(), price.getSourceSheet()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> findMissingItems(Long vendorId) {
        if (vendorId == null) return List.of();
        var registeredItems = vendorPriceRepository
                .findByVendor_IdOrderByItemNameAsc(vendorId)
                .stream()
                .map(VendorPriceEntity::getItemName)
                .toList();
        return ItemCatalog.ALL_ITEMS.stream()
                .filter(item -> !registeredItems.contains(item))
                .toList();
    }

    /** 기존 단가관리 화면 호환: 기본단가만 바꾸고 미단가 판매에만 채웁니다. */
    @Transactional
    public int updatePrice(Long priceId, BigDecimal unitPrice) {
        validateUnitPrice(unitPrice);
        VendorPriceEntity entity = findPrice(priceId);
        entity.update(normalizePrice(unitPrice), entity.getSourceSheet());
        return applyPricesToUnpricedSales();
    }

    /** 거래처 상세에서 수정할 때는 선택한 월의 '기존 기본단가' 주문만 새 기본단가로 바꿉니다. */
    @Transactional
    public int updatePriceForMonth(
            Long priceId,
            BigDecimal unitPrice,
            YearMonth month
    ) {
        validateUnitPrice(unitPrice);
        VendorPriceEntity entity = findPrice(priceId);
        BigDecimal oldConfiguredPrice = entity.getUnitPrice();
        BigDecimal newConfiguredPrice = normalizePrice(unitPrice);

        entity.update(newConfiguredPrice, entity.getSourceSheet());

        int updated = updateMonthSalesStillUsingOldBase(
                entity.getVendor().getId(),
                entity.getItemName(),
                oldConfiguredPrice,
                newConfiguredPrice,
                month
        );
        return updated + applyPricesToUnpricedSales();
    }

    @Transactional
    public int createOrUpdatePrice(
            Long vendorId,
            String itemName,
            BigDecimal unitPrice
    ) {
        return createOrUpdatePriceInternal(vendorId, itemName, unitPrice, null);
    }

    @Transactional
    public int createOrUpdatePriceForMonth(
            Long vendorId,
            String itemName,
            BigDecimal unitPrice,
            YearMonth month
    ) {
        return createOrUpdatePriceInternal(vendorId, itemName, unitPrice, month);
    }

    private int createOrUpdatePriceInternal(
            Long vendorId,
            String itemName,
            BigDecimal unitPrice,
            YearMonth month
    ) {
        validateUnitPrice(unitPrice);
        if (!ItemCatalog.ALL_ITEMS.contains(itemName)) {
            throw new IllegalArgumentException("지원하지 않는 품목입니다: " + itemName);
        }

        VendorEntity vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("거래처를 찾을 수 없습니다."));
        BigDecimal normalized = normalizePrice(unitPrice);
        var existing = vendorPriceRepository.findByVendor_IdAndItemName(vendorId, itemName);
        int updated = 0;

        if (existing.isPresent()) {
            BigDecimal oldConfiguredPrice = existing.get().getUnitPrice();
            existing.get().update(normalized, "웹 직접 입력");
            if (month != null) {
                updated = updateMonthSalesStillUsingOldBase(
                        vendorId,
                        itemName,
                        oldConfiguredPrice,
                        normalized,
                        month
                );
            }
        } else {
            vendorPriceRepository.save(new VendorPriceEntity(
                    vendor,
                    itemName,
                    normalized,
                    "웹 직접 입력"
            ));
        }

        return updated + applyPricesToUnpricedSales();
    }

    /**
     * 예: 기본단가 3,500원에서 4,000원으로 바꿀 때 선택 월의 3,500원 주문만 4,000원으로 변경합니다.
     * 8/13을 행사단가 3,000원으로 직접 바꿨다면 3,000원은 일치하지 않으므로 보존됩니다.
     */
    private int updateMonthSalesStillUsingOldBase(
            Long vendorId,
            String itemName,
            BigDecimal oldConfiguredPrice,
            BigDecimal newConfiguredPrice,
            YearMonth month
    ) {
        if (month == null || oldConfiguredPrice == null) return 0;

        BigDecimal oldSalesPrice = toSalesPrice(itemName, oldConfiguredPrice);
        BigDecimal newSalesPrice = toSalesPrice(itemName, newConfiguredPrice);
        int updated = 0;

        for (SalesItemEntity item : salesItemRepository.findForVendorPeriod(
                vendorId,
                month.atDay(1),
                month.atEndOfMonth()
        )) {
            if (!itemName.equals(item.getItemName())) continue;
            BigDecimal current = item.getUnitPrice();
            if (current != null && current.compareTo(oldSalesPrice) == 0) {
                item.applyUnitPrice(newSalesPrice);
                updated++;
            }
        }
        return updated;
    }

    private BigDecimal toSalesPrice(String itemName, BigDecimal configuredPrice) {
        return "회수통".equals(itemName)
                ? ReturnContainerPricePolicy.toSalesUnitPrice(configuredPrice)
                : configuredPrice;
    }

    private int applyPricesToUnpricedSales() {
        Map<String, BigDecimal> priceMap = new HashMap<>();
        for (VendorPriceEntity price : vendorPriceRepository.findAllWithVendor()) {
            priceMap.put(
                    priceKey(price.getVendor().getId(), price.getItemName()),
                    price.getUnitPrice()
            );
        }

        int applied = 0;
        for (SalesItemEntity salesItem : salesItemRepository.findAllWithoutUnitPrice()) {
            BigDecimal unitPrice = resolveUnitPrice(salesItem, priceMap);
            if (unitPrice != null) {
                salesItem.applyUnitPrice(unitPrice);
                applied++;
            }
        }
        return applied;
    }

    private BigDecimal resolveUnitPrice(
            SalesItemEntity salesItem,
            Map<String, BigDecimal> priceMap
    ) {
        if ("회수통".equals(salesItem.getItemName())) {
            BigDecimal configuredPrice = salesItem.getSalesOrder().getReturnContainerUnitPrice();
            if (configuredPrice == null) {
                configuredPrice = priceMap.get(priceKey(
                        salesItem.getSalesOrder().getVendor().getId(),
                        salesItem.getItemName()
                ));
            }
            return ReturnContainerPricePolicy.toSalesUnitPrice(configuredPrice);
        }
        return priceMap.get(priceKey(
                salesItem.getSalesOrder().getVendor().getId(),
                salesItem.getItemName()
        ));
    }

    private VendorPriceEntity findPrice(Long priceId) {
        return vendorPriceRepository.findById(priceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "수정할 단가 정보를 찾을 수 없습니다."
                ));
    }

    private String priceKey(Long vendorId, String itemName) {
        return vendorId + "\u0000" + itemName;
    }

    private BigDecimal normalizePrice(BigDecimal unitPrice) {
        return unitPrice.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null) {
            throw new IllegalArgumentException("단가를 입력해주세요.");
        }
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("단가는 0원 이상이어야 합니다.");
        }
    }
}
