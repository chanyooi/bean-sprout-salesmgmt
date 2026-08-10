package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.BeanUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BeanUsageRepository extends JpaRepository<BeanUsageEntity, Long> {

    List<BeanUsageEntity> findAllByUsageDateLessThanEqualOrderByUsageDateAscIdAsc(
            LocalDate usageDate
    );

    List<BeanUsageEntity> findAllByUsageDateBetweenOrderByUsageDateAscIdAsc(
            LocalDate startDate,
            LocalDate endDate
    );

    List<BeanUsageEntity> findTop50ByOrderByUsageDateDescIdDesc();
}
