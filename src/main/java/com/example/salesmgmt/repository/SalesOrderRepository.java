package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.SalesOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrderEntity, Long> {
    Optional<SalesOrderEntity> findByOrderNumber(String orderNumber);
}
