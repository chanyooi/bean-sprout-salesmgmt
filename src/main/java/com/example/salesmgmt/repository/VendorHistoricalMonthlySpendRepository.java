package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.VendorHistoricalMonthlySpendEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VendorHistoricalMonthlySpendRepository
        extends JpaRepository<VendorHistoricalMonthlySpendEntity, Long> {

    Optional<VendorHistoricalMonthlySpendEntity> findByVendor_IdAndSpendMonth(
            Long vendorId,
            LocalDate spendMonth
    );

    List<VendorHistoricalMonthlySpendEntity> findAllByVendor_IdAndSpendMonthBetween(
            Long vendorId,
            LocalDate start,
            LocalDate end
    );
}
