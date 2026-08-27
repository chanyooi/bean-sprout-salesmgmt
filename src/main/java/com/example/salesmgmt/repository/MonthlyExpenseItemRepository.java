package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.MonthlyExpenseItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MonthlyExpenseItemRepository extends JpaRepository<MonthlyExpenseItemEntity, Long> {

    List<MonthlyExpenseItemEntity> findAllByMonthStartOrderByCategoryAscIdAsc(LocalDate monthStart);

    long countByMonthStart(LocalDate monthStart);

    Optional<MonthlyExpenseItemEntity> findByIdAndMonthStart(Long id, LocalDate monthStart);

    void deleteAllByMonthStart(LocalDate monthStart);
}
