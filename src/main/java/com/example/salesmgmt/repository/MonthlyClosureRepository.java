package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.MonthlyClosureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MonthlyClosureRepository
        extends JpaRepository<MonthlyClosureEntity, Long> {

    Optional<MonthlyClosureEntity> findByMonthKey(String monthKey);
    boolean existsByMonthKey(String monthKey);
    List<MonthlyClosureEntity> findAllByOrderByMonthKeyDesc();
    long deleteByMonthKey(String monthKey);
}
