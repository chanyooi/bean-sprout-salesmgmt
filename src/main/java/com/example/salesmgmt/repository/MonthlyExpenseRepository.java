package com.example.salesmgmt.repository;

import com.example.salesmgmt.domain.ExpenseType;
import com.example.salesmgmt.entity.MonthlyExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MonthlyExpenseRepository extends JpaRepository<MonthlyExpenseEntity, Long> {

    List<MonthlyExpenseEntity> findAllByMonthStartOrderByExpenseTypeAsc(LocalDate monthStart);

    Optional<MonthlyExpenseEntity> findByMonthStartAndExpenseType(
            LocalDate monthStart,
            ExpenseType expenseType
    );
}
