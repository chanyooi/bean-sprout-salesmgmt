package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.WeeklySettlementPeriodEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeeklySettlementPeriodRepository extends JpaRepository<WeeklySettlementPeriodEntity, Long> {
    Optional<WeeklySettlementPeriodEntity> findByVendor_IdAndWeekAnchor(Long vendorId, LocalDate anchor);

    @Query("""
            select p from WeeklySettlementPeriodEntity p join fetch p.vendor
            where p.weekAnchor = :start or (p.startsOn <= :end and p.endsOn >= :start)
            """)
    List<WeeklySettlementPeriodEntity> findForCalendarWeek(LocalDate start, LocalDate end);

    List<WeeklySettlementPeriodEntity> findByVendor_IdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqual(
            Long vendorId, LocalDate end, LocalDate start);
}
