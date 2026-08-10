package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.StatementDeliverySettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StatementDeliverySettingRepository
        extends JpaRepository<StatementDeliverySettingEntity, Long> {

    Optional<StatementDeliverySettingEntity> findByVendor_Id(Long vendorId);

    List<StatementDeliverySettingEntity> findAllByOrderByVendor_InputNameAsc();
}
