package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class SpecialItemAccountingService {

    private final SalesItemRepository salesItemRepository;

    public SpecialItemAccountingService(SalesItemRepository salesItemRepository) {
        this.salesItemRepository = salesItemRepository;
    }

    @Transactional(readOnly = true)
    public SpecialItemAccountingReport report(YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        List<SalesItemEntity> items = salesItemRepository.findForMonthlyReport(start, end);

        BigDecimal legacySales = BigDecimal.ZERO;
        BigDecimal directSales = BigDecimal.ZERO;

        BigDecimal tofuPurchaseCost = BigDecimal.ZERO;
        BigDecimal tofuResaleSales = BigDecimal.ZERO;
        BigDecimal tofuOtherAmount = BigDecimal.ZERO;
        BigDecimal tofuPurchaseQty = BigDecimal.ZERO;
        BigDecimal tofuResaleQty = BigDecimal.ZERO;

        BigDecimal trayAmount = BigDecimal.ZERO;
        BigDecimal trayQty = BigDecimal.ZERO;

        BigDecimal returnContainerAmount = BigDecimal.ZERO;
        BigDecimal returnContainerQty = BigDecimal.ZERO;

        for (SalesItemEntity item : items) {
            String itemName = normalize(item.getItemName());
            BigDecimal amount = nz(item.getLineAmount());
            BigDecimal qty = nz(item.getQuantity());
            String vendor = normalize(
                    item.getSalesOrder().getVendor().getStatementName()
            );

            legacySales = legacySales.add(amount);

            if ("손두부".equals(itemName)) {
                if (vendor.contains("팔공")) {
                    tofuPurchaseCost = tofuPurchaseCost.add(amount.abs());
                    tofuPurchaseQty = tofuPurchaseQty.add(qty.abs());
                } else if (vendor.contains("아포농협")) {
                    tofuResaleSales = tofuResaleSales.add(amount);
                    tofuResaleQty = tofuResaleQty.add(qty.abs());
                } else {
                    tofuOtherAmount = tofuOtherAmount.add(amount);
                }
                continue;
            }

            if ("두부판".equals(itemName)) {
                trayAmount = trayAmount.add(amount);
                trayQty = trayQty.add(qty.abs());
                continue;
            }

            if ("회수통".equals(itemName)) {
                returnContainerAmount = returnContainerAmount.add(amount);
                returnContainerQty = returnContainerQty.add(qty.abs());
                continue;
            }

            directSales = directSales.add(amount);
        }

        // 송천의 실제 판매매출 = 자체 판매품목 + 아포농협에 판매한 손두부.
        // 팔공 손두부 매입, 두부판, 회수통은 일반 매출에서 제외한다.
        BigDecimal adjustedSales = directSales.add(tofuResaleSales);
        BigDecimal tofuProfit = tofuResaleSales.subtract(tofuPurchaseCost);

        // 기존 예상이익이 legacySales를 매출로 사용하고 손두부 매입을 별도 원가로
        // 빼지 않았다는 전제에서 적용하는 보정값.
        BigDecimal profitAdjustment = adjustedSales
                .subtract(legacySales)
                .subtract(tofuPurchaseCost);

        return new SpecialItemAccountingReport(
                month.toString(),
                legacySales,
                directSales,
                adjustedSales,
                tofuPurchaseQty,
                tofuResaleQty,
                tofuPurchaseCost,
                tofuResaleSales,
                tofuProfit,
                tofuOtherAmount,
                trayQty,
                trayAmount,
                returnContainerQty,
                returnContainerAmount,
                profitAdjustment
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record SpecialItemAccountingReport(
            String month,
            BigDecimal legacySales,
            BigDecimal directSales,
            BigDecimal adjustedSales,
            BigDecimal tofuPurchaseQty,
            BigDecimal tofuResaleQty,
            BigDecimal tofuPurchaseCost,
            BigDecimal tofuResaleSales,
            BigDecimal tofuProfit,
            BigDecimal tofuOtherAmount,
            BigDecimal tofuTrayQty,
            BigDecimal tofuTrayAmount,
            BigDecimal returnContainerQty,
            BigDecimal returnContainerAmount,
            BigDecimal profitAdjustment
    ) {}
}
