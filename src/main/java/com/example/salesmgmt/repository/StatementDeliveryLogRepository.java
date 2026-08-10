package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.StatementDeliveryLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StatementDeliveryLogRepository
        extends JpaRepository<StatementDeliveryLogEntity, Long> {

    Optional<StatementDeliveryLogEntity>
    findByVendor_IdAndMonthKey(Long vendorId, String monthKey);

    long deleteByVendor_IdAndMonthKey(
            Long vendorId,
            String monthKey
    );
}
