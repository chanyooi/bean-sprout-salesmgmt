package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.PromotionViewRow;
import com.example.salesmgmt.entity.SalesItemEntity;
import com.example.salesmgmt.entity.SalesPromotionEntity;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.repository.SalesItemRepository;
import com.example.salesmgmt.repository.SalesPromotionRepository;
import com.example.salesmgmt.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class SalesPromotionService {

    private final SalesPromotionRepository promotionRepository;
    private final VendorRepository vendorRepository;
    private final SalesItemRepository salesItemRepository;
    private final MonthlyCloseService monthlyCloseService;

    public SalesPromotionService(
            SalesPromotionRepository promotionRepository,
            VendorRepository vendorRepository,
            SalesItemRepository salesItemRepository,
            MonthlyCloseService monthlyCloseService
    ) {
        this.promotionRepository = promotionRepository;
        this.vendorRepository = vendorRepository;
        this.salesItemRepository = salesItemRepository;
        this.monthlyCloseService = monthlyCloseService;
    }

    @Transactional(readOnly = true)
    public List<PromotionViewRow> findAll() {
        return promotionRepository
                .findAllByOrderByStartDateDescCreatedAtDesc()
                .stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal findEventPrice(
            Long vendorId,
            String itemName,
            LocalDate date
    ) {
        if (vendorId == null
                || itemName == null
                || date == null) {
            return null;
        }

        return promotionRepository
                .findFirstByVendor_IdAndItemNameAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByCreatedAtDesc(
                        vendorId,
                        itemName.trim(),
                        date,
                        date
                )
                .map(SalesPromotionEntity::getPromotionUnitPrice)
                .orElse(null);
    }

    @Transactional
    public void create(
            Long vendorId,
            String itemName,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal promotionUnitPrice,
            String memo
    ) {
        VendorEntity vendor = vendorRepository
                .findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "거래처를 찾을 수 없습니다."
                ));

        String normalizedItem =
                itemName == null ? "" : itemName.trim();

        if (!promotionRepository.findOverlapping(
                vendorId,
                normalizedItem,
                startDate,
                endDate
        ).isEmpty()) {
            throw new IllegalArgumentException(
                    "같은 거래처·품목에 겹치는 행사기간이 이미 있습니다."
            );
        }

        promotionRepository.save(
                new SalesPromotionEntity(
                        vendor,
                        normalizedItem,
                        startDate,
                        endDate,
                        promotionUnitPrice,
                        memo
                )
        );
    }

    @Transactional
    public void update(
            Long promotionId,
            BigDecimal promotionUnitPrice,
            String memo
    ) {
        SalesPromotionEntity promotion =
                findEntity(promotionId);

        promotion.updatePriceAndMemo(
                promotionUnitPrice,
                memo
        );
    }

    /**
     * 이미 업로드되어 있는 판매자료에 행사단가를 일괄 반영합니다.
     * 마감된 월의 판매가 하나라도 포함되어 있으면 전체 작업을 중단합니다.
     */
    @Transactional
    public int applyToExistingSales(Long promotionId) {
        SalesPromotionEntity promotion =
                findEntity(promotionId);

        if (promotion.getPromotionUnitPrice() == null) {
            throw new IllegalArgumentException(
                    "행사 단가를 먼저 입력해주세요."
            );
        }

        List<SalesItemEntity> matching =
                salesItemRepository.findForMonthlyReport(
                        promotion.getStartDate(),
                        promotion.getEndDate()
                )
                .stream()
                .filter(item -> Objects.equals(
                        item.getSalesOrder().getVendor().getId(),
                        promotion.getVendor().getId()
                ))
                .filter(item -> promotion.getItemName().equals(
                        item.getItemName()
                ))
                .toList();

        for (SalesItemEntity item : matching) {
            monthlyCloseService.assertOpen(
                    item.getSalesOrder().getDeliveryDate()
            );
        }

        for (SalesItemEntity item : matching) {
            item.applyUnitPrice(
                    promotion.getPromotionUnitPrice()
            );
        }

        salesItemRepository.saveAll(matching);
        return matching.size();
    }

    @Transactional
    public void delete(Long promotionId) {
        promotionRepository.delete(
                findEntity(promotionId)
        );
    }

    private SalesPromotionEntity findEntity(Long id) {
        return promotionRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "행사 기록을 찾을 수 없습니다."
                ));
    }

    private PromotionViewRow toRow(
            SalesPromotionEntity entity
    ) {
        return new PromotionViewRow(
                entity.getId(),
                entity.getVendor().getId(),
                entity.getVendor().getInputName(),
                entity.getItemName(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getPromotionUnitPrice(),
                entity.getMemo(),
                entity.getCreatedAt()
        );
    }
}
