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
                            row.inputVendor(),
                            row.statementVendor(),
                            true
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
                applyBasePriceToSales(vendor.getId(), row.itemName(), normalizedPrice);
                createdPrices++;
                continue;
            }

            VendorPriceEntity priceEntity = existingPrice.get();

            if (priceEntity.getUnitPrice().compareTo(normalizedPrice) == 0) {
                unchangedPrices++;
            } else {
                priceEntity.update(normalizedPrice, row.sourceSheet());
                applyBasePriceToSales(vendor.getId(), row.itemName(), normalizedPrice);
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
                        vendor.getId(),
                        vendor.getInputName(),
                        vendor.getStatementName()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PriceViewRow> findPrices(Long vendorId) {
        if (vendorId == null) {
            return List.of();
        }

        return vendorPriceRepository.findByVendor_IdOrderByItemNameAsc(vendorId)
                .stream()
                .map(price -> new PriceViewRow(
                        price.getId(),
                        price.getItemName(),
                        price.getUnitPrice(),
                        price.getSourceSheet()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> findMissingItems(Long vendorId) {
        if (vendorId == null) {
            return List.of();
        }

        var registeredItems = vendorPriceRepository
                .findByVendor_IdOrderByItemNameAsc(vendorId)
                .stream()
                .map(VendorPriceEntity::getItemName)
                .toList();

        return ItemCatalog.ALL_ITEMS.stream()
                .filter(item -> !registeredItems.contains(item))
                .toList();
    }

    @Transactional
    public int updatePrice(Long priceId, BigDecimal unitPrice) {
        validateUnitPrice(unitPrice);

        VendorPriceEntity entity = vendorPriceRepository.findById(priceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "수정할 단가 정보를 찾을 수 없습니다."
                ));

        BigDecimal normalized = normalizePrice(unitPrice);
        entity.update(normalized, entity.getSourceSheet());
        int updated = applyBasePriceToSales(
                entity.getVendor().getId(),
                entity.getItemName(),
                normalized
        );
        return updated + applyPricesToUnpricedSales();
    }

    @Transactional
    public int createOrUpdatePrice(
            Long vendorId,
            String itemName,
            BigDecimal unitPrice
    ) {
        validateUnitPrice(unitPrice);

        if (!ItemCatalog.ALL_ITEMS.contains(itemName)) {
            throw new IllegalArgumentException("지원하지 않는 품목입니다: " + itemName);
        }

        VendorEntity vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "거래처를 찾을 수 없습니다."
                ));

        BigDecimal normalized = normalizePrice(unitPrice);
        var existing = vendorPriceRepository.findByVendor_IdAndItemName(vendorId, itemName);

        if (existing.isPresent()) {
            existing.get().update(normalized, "웹 직접 입력");
        } else {
            vendorPriceRepository.save(new VendorPriceEntity(
                    vendor,
                    itemName,
                    normalized,
                    "웹 직접 입력"
            ));
        }

        int updated = applyBasePriceToSales(vendorId, itemName, normalized);
        return updated + applyPricesToUnpricedSales();
    }

    private int applyBasePriceToSales(
            Long vendorId,
            String itemName,
            BigDecimal configuredPrice
    ) {
        int updated = 0;
        for (SalesItemEntity item : salesItemRepository.findBasePriceManagedItems(vendorId, itemName)) {
            BigDecimal salesPrice = "회수통".equals(itemName)
                    ? ReturnContainerPricePolicy.toSalesUnitPrice(configuredPrice)
                    : configuredPrice;
            item.applyBaseUnitPrice(salesPrice);
            updated++;
        }
        return updated;
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
                salesItem.applyBaseUnitPrice(unitPrice);
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
            BigDecimal configuredPrice =
                    salesItem.getSalesOrder().getReturnContainerUnitPrice();

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
