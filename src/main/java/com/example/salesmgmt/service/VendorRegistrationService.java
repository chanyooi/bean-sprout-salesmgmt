package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.ItemCatalog;
import com.example.salesmgmt.domain.PaymentCycle;
import com.example.salesmgmt.domain.RouteCode;
import com.example.salesmgmt.domain.StatementDeliveryMethod;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.entity.VendorPriceEntity;
import com.example.salesmgmt.entity.VendorProfileEntity;
import com.example.salesmgmt.repository.VendorPriceRepository;
import com.example.salesmgmt.repository.VendorProfileRepository;
import com.example.salesmgmt.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
public class VendorRegistrationService {

    private final VendorRepository vendorRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final VendorPriceRepository vendorPriceRepository;

    public VendorRegistrationService(
            VendorRepository vendorRepository,
            VendorProfileRepository vendorProfileRepository,
            VendorPriceRepository vendorPriceRepository
    ) {
        this.vendorRepository = vendorRepository;
        this.vendorProfileRepository = vendorProfileRepository;
        this.vendorPriceRepository = vendorPriceRepository;
    }

    @Transactional
    public Long register(
            String inputName,
            String statementName,
            String phone,
            String address,
            PaymentCycle paymentCycle,
            StatementDeliveryMethod deliveryMethod,
            Map<String, BigDecimal> prices
    ) {
        String safeInputName = normalizeName(inputName, "거래처명을 입력해주세요.");
        String safeStatementName = statementName == null || statementName.isBlank()
                ? safeInputName
                : statementName.trim();

        if (vendorRepository.findByInputName(safeInputName).isPresent()) {
            throw new IllegalArgumentException("이미 등록된 거래처명입니다: " + safeInputName);
        }

        VendorEntity vendor = new VendorEntity(
                safeInputName,
                safeStatementName,
                true
        );
        vendor.updateStatementDeliveryMethod(deliveryMethod);
        vendor = vendorRepository.save(vendor);

        VendorProfileEntity profile = new VendorProfileEntity(vendor);
        profile.update(
                true,
                RouteCode.NONE,
                null,
                address,
                phone,
                paymentCycle,
                null,
                null,
                null
        );
        vendorProfileRepository.save(profile);

        if (prices != null) {
            for (Map.Entry<String, BigDecimal> entry : prices.entrySet()) {
                if (!ItemCatalog.ALL_ITEMS.contains(entry.getKey())) {
                    continue;
                }
                BigDecimal price = entry.getValue();
                if (price == null) {
                    continue;
                }
                if (price.signum() < 0) {
                    throw new IllegalArgumentException(entry.getKey() + " 단가는 0원 이상이어야 합니다.");
                }
                vendorPriceRepository.save(new VendorPriceEntity(
                        vendor,
                        entry.getKey(),
                        price.setScale(2, RoundingMode.HALF_UP),
                        "거래처 추가"
                ));
            }
        }

        return vendor.getId();
    }

    private String normalizeName(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        String normalized = value.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("거래처명은 100자 이하로 입력해주세요.");
        }
        return normalized;
    }
}
