package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReceivableBillingAdjustmentService {

    private static final BigDecimal TOFU_TRAY_RETURN_REVENUE_PER_UNIT =
            new BigDecimal("2000");

    private final SalesItemRepository salesItemRepository;

    public ReceivableBillingAdjustmentService(
            SalesItemRepository salesItemRepository
    ) {
        this.salesItemRepository = salesItemRepository;
    }

    /**
     * 월매출 화면의 특수품목 보정 기준과 미수금 청구액 기준을 맞춘다.
     * 일반 품목/행사 단가는 lineAmount 자체가 이미 수정되므로 별도 보정이 없다.
     * 손두부 매입은 매출에서 제외하고, 두부판은 DB 기존 금액 대신 판당 2,000원으로 반영한다.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> correctionsByVendor(YearMonth month) {
        List<SalesItemEntity> items = salesItemRepository.findSpecialItemsForMonthlyBilling(
                month.atDay(1),
                month.atEndOfMonth()
        );

        Map<Long, BigDecimal> corrections = new HashMap<>();

        for (SalesItemEntity item : items) {
            Long vendorId = item.getSalesOrder().getVendor().getId();
            BigDecimal correction = billingAmount(item).subtract(nz(item.getLineAmount()));

            if (correction.signum() != 0) {
                corrections.merge(vendorId, correction, BigDecimal::add);
            }
        }

        return Map.copyOf(corrections);
    }

    public static BigDecimal billingAmount(SalesItemEntity item) {
        String name = normalize(item.getItemName());
        if ("손두부".equals(name)
                && !normalize(item.getSalesOrder().getVendor().getStatementName()).contains("아포농협")) {
            return BigDecimal.ZERO;
        }
        if ("두부판".equals(name)) {
            return nz(item.getQuantity()).abs().multiply(TOFU_TRAY_RETURN_REVENUE_PER_UNIT);
        }
        return nz(item.getLineAmount());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
