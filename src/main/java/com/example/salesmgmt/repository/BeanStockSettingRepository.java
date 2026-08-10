package com.example.salesmgmt.repository;

import com.example.salesmgmt.domain.BeanOrigin;
import com.example.salesmgmt.domain.BeanType;
import com.example.salesmgmt.entity.BeanStockSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BeanStockSettingRepository extends JpaRepository<BeanStockSettingEntity, Long> {

    Optional<BeanStockSettingEntity> findByBeanTypeAndOrigin(
            BeanType beanType,
            BeanOrigin origin
    );
}
