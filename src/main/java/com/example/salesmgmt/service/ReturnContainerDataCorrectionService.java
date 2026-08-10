package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import com.example.salesmgmt.repository.VendorPriceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ReturnContainerDataCorrectionService {

    private static final String RETURN_CONTAINER = "회수통";

    private final SalesItemRepository salesItemRepository;
    private final VendorPriceRepository vendorPriceRepository;

    public ReturnContainerDataCorrectionService(
            SalesItemRepository salesItemRepository,
            VendorPriceRepository vendorPriceRepository
    ) {
        this.salesItemRepository = salesItemRepository;
        this.vendorPriceRepository = vendorPriceRepository;
    }

    /**
     * 이전 버전에서 +3000원처럼 저장된 회수통 판매자료를 바로잡습니다.
     *
     * - 양수 단가: 같은 금액의 음수로 변경
     * - 단가 null: 주문/거래처에 회수통 단가가 있으면 음수 적용
     * - 단가 null + 설정 없음: 보증금 없는 거래처로 보고 0원 적용
     * - 이미 음수 또는 0원: 그대로 유지
     *
     * 양수였던 기존 판매단가는 그 당시 금액 자체를 뒤집기 때문에,
     * 현재 거래처 단가가 변경되었더라도 과거 금액을 훼손하지 않습니다.
     */
    @Transactional
    public int correctExistingSales() {
        int corrected = 0;

        for (SalesItemEntity item
                : salesItemRepository.findAllByItemName(RETURN_CONTAINER)) {

            BigDecimal currentPrice = item.getUnitPrice();

            if (currentPrice != null && currentPrice.signum() <= 0) {
                continue;
            }

            BigDecimal targetPrice;

            if (currentPrice != null) {
                targetPrice = ReturnContainerPricePolicy
                        .toSalesUnitPrice(currentPrice);
            } else {
                BigDecimal configuredPrice = item.getSalesOrder()
                        .getReturnContainerUnitPrice();

                if (configuredPrice == null) {
                    configuredPrice = vendorPriceRepository
                            .findByVendor_IdAndItemName(
                                    item.getSalesOrder()
                                            .getVendor()
                                            .getId(),
                                    RETURN_CONTAINER
                            )
                            .map(price -> price.getUnitPrice())
                            .orElse(null);
                }

                targetPrice = ReturnContainerPricePolicy
                        .toSalesUnitPrice(configuredPrice);
            }

            item.applyUnitPrice(targetPrice);
            corrected++;
        }

        return corrected;
    }
}
