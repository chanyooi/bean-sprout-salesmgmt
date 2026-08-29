package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.InputWorkbookSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InputWorkbookSnapshotRepository
        extends JpaRepository<InputWorkbookSnapshotEntity, Long> {

    Optional<InputWorkbookSnapshotEntity> findByMonthKey(String monthKey);
}
