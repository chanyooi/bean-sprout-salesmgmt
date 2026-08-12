package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.BeanPurchaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BeanPurchaseRepository extends JpaRepository<BeanPurchaseEntity, Long> {

    List<BeanPurchaseEntity> findAllByPurchaseDateLessThanEqualOrderByPurchaseDateAscIdAsc(
            LocalDate purchaseDate
    );

    List<BeanPurchaseEntity> findAllByPurchaseDateBetweenOrderByPurchaseDateAscIdAsc(
            LocalDate startDate,
            LocalDate endDate
    );

    List<BeanPurchaseEntity> findAllByOrderByPurchaseDateDescIdDesc();

    List<BeanPurchaseEntity> findTop50ByOrderByPurchaseDateDescIdDesc();
}
