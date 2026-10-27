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

    public SpecialItemAccountingService(
            SalesItemRepository salesItemRepository
    ) {
        this.salesItemRepository = salesItemRepository;
    }

    @Transactional(readOnly = true)
    public SpecialItemAccountingReport report(
            YearMonth month
    ) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        List<SalesItemEntity> items =
                salesItemRepository.findForMonthlyReport(
                        start,
                        end
                );

        BigDecimal legacySales = BigDecimal.ZERO;
        BigDecimal normalSalesIncludingReturnContainer = BigDecimal.ZERO;

        BigDecimal tofuPurchaseCost = BigDecimal.ZERO;
        BigDecimal tofuResaleSales = BigDecimal.ZERO;

        BigDecimal tofuPurchaseQty = BigDecimal.ZERO;
        BigDecimal tofuResaleQty = BigDecimal.ZERO;

        BigDecimal tofuTrayQty = BigDecimal.ZERO;
        BigDecimal tofuTrayAdjustedAmount = BigDecimal.ZERO;
        BigDecimal tofuTrayRecordedAmount = BigDecimal.ZERO;

        for (SalesItemEntity item : items) {
            String itemName =
                    normalize(
                            item.getItemName()
                    );

            BigDecimal amount =
                    nz(
                            item.getLineAmount()
                    );

            BigDecimal quantity =
                    nz(
                            item.getQuantity()
                    );

            String vendorName =
                    normalize(
                            item.getSalesOrder()
                                    .getVendor()
                                    .getStatementName()
                    );

            legacySales =
                    legacySales.add(
                            amount
                    );

            if ("손두부".equals(itemName)) {
                if (vendorName.contains("팔공")) {
                    tofuPurchaseCost =
                            tofuPurchaseCost.add(
                                    amount.abs()
                            );

                    tofuPurchaseQty =
                            tofuPurchaseQty.add(
                                    quantity.abs()
                            );
                } else if (vendorName.contains("아포농협")) {
                    tofuResaleSales =
                            tofuResaleSales.add(
                                    amount
                            );

                    tofuResaleQty =
                            tofuResaleQty.add(
                                    quantity.abs()
                            );
                }

                continue;
            }

            if ("두부판".equals(itemName)) {
                tofuTrayQty =
                        tofuTrayQty.add(
                                quantity.abs()
                        );

                tofuTrayRecordedAmount =
                        tofuTrayRecordedAmount.add(
                                amount
                        );
                tofuTrayAdjustedAmount = tofuTrayAdjustedAmount.add(
                        ReceivableBillingAdjustmentService.billingAmount(item));

                continue;
            }

            /*
             * 회수통은 일반 매출에 다시 포함한다.
             * 음수 금액이면 기존 화면과 동일하게 매출에서 차감된다.
             */
            normalSalesIncludingReturnContainer =
                    normalSalesIncludingReturnContainer.add(
                            amount
                    );
        }

        /*
         * 최신 운영 기준:
         * - 팔공 두부판 반환은 1판당 -1,000원 보증금 차감.
         * - 다른 거래처 두부판은 기존 2,000원 정책 유지.
         * - 기존 저장 금액 대신 청구액 계산 규칙을 재사용.
         */
        BigDecimal tofuTrayReturnRevenue = tofuTrayAdjustedAmount;

        BigDecimal tofuTotalRevenue =
                tofuResaleSales.add(
                        tofuTrayReturnRevenue
                );

        BigDecimal tofuProfit =
                tofuTotalRevenue.subtract(
                        tofuPurchaseCost
                );

        BigDecimal adjustedSales =
                normalSalesIncludingReturnContainer
                        .add(
                                tofuTotalRevenue
                        );

        /*
         * 이전 화면의 예상이익에 더하거나 뺄 보정값.
         * 기존 전체 품목 금액 합계(legacySales)와
         * 새 매출 기준(adjustedSales)의 차이를 적용한다.
         * 손두부 매입원가도 새 비용으로 차감한다.
         */
        BigDecimal profitAdjustment =
                adjustedSales
                        .subtract(
                                legacySales
                        )
                        .subtract(
                                tofuPurchaseCost
                        );

        return new SpecialItemAccountingReport(
                month.toString(),
                legacySales,
                adjustedSales,
                tofuPurchaseQty,
                tofuResaleQty,
                tofuPurchaseCost,
                tofuResaleSales,
                tofuTrayQty,
                tofuTrayRecordedAmount,
                tofuTrayReturnRevenue,
                tofuTotalRevenue,
                tofuProfit,
                profitAdjustment
        );
    }

    private static String normalize(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    private static BigDecimal nz(
            BigDecimal value
    ) {
        return value == null
                ? BigDecimal.ZERO
                : value;
    }

    public record SpecialItemAccountingReport(
            String month,
            BigDecimal legacySales,
            BigDecimal adjustedSales,
            BigDecimal tofuPurchaseQty,
            BigDecimal tofuResaleQty,
            BigDecimal tofuPurchaseCost,
            BigDecimal tofuResaleSales,
            BigDecimal tofuTrayQty,
            BigDecimal tofuTrayRecordedAmount,
            BigDecimal tofuTrayReturnRevenue,
            BigDecimal tofuTotalRevenue,
            BigDecimal tofuProfit,
            BigDecimal profitAdjustment
    ) {
    }
}
