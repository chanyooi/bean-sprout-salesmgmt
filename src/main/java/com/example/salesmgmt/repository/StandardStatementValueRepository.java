package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.StandardStatementValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StandardStatementValueRepository extends JpaRepository<StandardStatementValueEntity, Long> {
    List<StandardStatementValueEntity> findByYear(int year);
    List<StandardStatementValueEntity> findByYearAndType(int year, String type);
}
