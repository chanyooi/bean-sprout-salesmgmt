package com.example.salesmgmt.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 회수통 금액 정책.
 *
 * 거래처 단가 설정에는 공제 기준 금액을 양수(예: 3000)로 저장하고,
 * 실제 판매금액에는 음수(예: -3000)로 반영합니다.
 *
 * 회수통 단가가 설정되지 않은 거래처는 보증금을 받지 않는 거래처로 보고
 * 판매 반영단가를 0원으로 처리합니다.
 */
public final class ReturnContainerPricePolicy {

    private static final int PRICE_SCALE = 2;

    private ReturnContainerPricePolicy() {
    }

    public static BigDecimal toSalesUnitPrice(BigDecimal configuredPrice) {
        if (configuredPrice == null || configuredPrice.signum() == 0) {
            return BigDecimal.ZERO.setScale(
                    PRICE_SCALE,
                    RoundingMode.HALF_UP
            );
        }

        return configuredPrice
                .abs()
                .negate()
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
