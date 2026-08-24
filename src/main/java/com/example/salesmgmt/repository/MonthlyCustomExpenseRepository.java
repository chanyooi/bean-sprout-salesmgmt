package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.MonthlyCustomExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MonthlyCustomExpenseRepository extends JpaRepository<MonthlyCustomExpenseEntity, Long> {

    List<MonthlyCustomExpenseEntity> findAllByMonthStartOrderBySortOrderAscIdAsc(LocalDate monthStart);

    void deleteAllByMonthStart(LocalDate monthStart);
}
