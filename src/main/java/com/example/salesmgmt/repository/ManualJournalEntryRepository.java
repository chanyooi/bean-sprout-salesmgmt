package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.ManualJournalEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface ManualJournalEntryRepository extends JpaRepository<ManualJournalEntryEntity, Long> {
    List<ManualJournalEntryEntity> findByEntryDateBetweenOrderByEntryDateAscIdAsc(LocalDate from, LocalDate to);
}
