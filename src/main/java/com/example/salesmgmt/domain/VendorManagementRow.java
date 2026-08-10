package com.example.salesmgmt.domain;

import java.math.BigDecimal;

public record VendorManagementRow(
        Long vendorId,
        String vendorName,
        String statementName,
        boolean active,
        RouteCode routeCode,
        Integer routeOrder,
        String address,
        String phone,
        PaymentCycle paymentCycle,
        String memo,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal returnContainerDepositPrice
) {
    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    public boolean hasReturnContainerDeposit() {
        return returnContainerDepositPrice != null
                && returnContainerDepositPrice.signum() > 0;
    }
}
