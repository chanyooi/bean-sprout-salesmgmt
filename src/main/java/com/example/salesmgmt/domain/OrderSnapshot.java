package com.example.salesmgmt.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * input_data.xlsx의 거래처 한 줄 전체 상태입니다.
 * 품목 수량이 전부 비어 있어도 이 정보는 남겨서,
 * 이전에 DB에 저장된 테스트/오입력 주문을 지울 수 있습니다.
 */
public record OrderSnapshot(
        String orderNumber,
        LocalDate deliveryDate,
        String inputVendor,
        String statementVendor,
        BigDecimal returnContainerUnitPrice,
        String deliveryMethod,
        String note,
        String sourceSheet,
        int sourceRow
) {
}
