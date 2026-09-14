package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.SalesItemEntity;
import java.math.BigDecimal;

/** The existing receivables policy, shared by all customer billing totals. */
public final class BillingAmountPolicy {
    private BillingAmountPolicy() {}
    public static boolean hasFixedAmount(SalesItemEntity item) {
        String name = item.getItemName() == null ? "" : item.getItemName().trim();
        if ("두부판".equals(name)) return true;
        String statement = item.getSalesOrder().getVendor().getStatementName();
        return "손두부".equals(name) && (statement == null || !statement.trim().contains("아포농협"));
    }
    public static BigDecimal amount(SalesItemEntity item) {
        String name = item.getItemName() == null ? "" : item.getItemName().trim();
        BigDecimal recorded = item.getLineAmount() == null ? BigDecimal.ZERO : item.getLineAmount();
        if ("손두부".equals(name)) {
            String statement = item.getSalesOrder().getVendor().getStatementName();
            return statement != null && statement.trim().contains("아포농협") ? recorded : BigDecimal.ZERO;
        }
        if ("두부판".equals(name)) {
            return item.getQuantity().abs().multiply(new BigDecimal("2000"));
        }
        return recorded;
    }
}
