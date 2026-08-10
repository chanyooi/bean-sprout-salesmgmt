package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.SalesPromotionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalesPromotionRepository
        extends JpaRepository<SalesPromotionEntity, Long> {

    List<SalesPromotionEntity> findAllByOrderByStartDateDescCreatedAtDesc();

    Optional<SalesPromotionEntity>
    findFirstByVendor_IdAndItemNameAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByCreatedAtDesc(
            Long vendorId,
            String itemName,
            LocalDate date1,
            LocalDate date2
    );

    @Query("""
            select promotion
            from SalesPromotionEntity promotion
            where promotion.vendor.id = :vendorId
              and promotion.itemName = :itemName
              and promotion.startDate <= :endDate
              and promotion.endDate >= :startDate
            order by promotion.startDate asc
            """)
    List<SalesPromotionEntity> findOverlapping(
            @Param("vendorId") Long vendorId,
            @Param("itemName") String itemName,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
