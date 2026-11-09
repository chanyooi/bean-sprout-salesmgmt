package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.ManualBookkeepingMonthlyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ManualBookkeepingMonthlyRepository extends JpaRepository<ManualBookkeepingMonthlyEntity, Long> {
    List<ManualBookkeepingMonthlyEntity> findByYearOrderByMonthAscCategoryCodeAsc(int year);
    List<ManualBookkeepingMonthlyEntity> findByYearAndMonth(int year, int month);
}
