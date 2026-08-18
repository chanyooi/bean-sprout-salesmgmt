package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.SalesOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrderEntity, Long> {
    Optional<SalesOrderEntity> findByOrderNumber(String orderNumber);

    @Query("""
            select orderEntity
            from SalesOrderEntity orderEntity
            join fetch orderEntity.vendor
            order by orderEntity.id asc
            """)
    List<SalesOrderEntity> findAllForBackup();
}
