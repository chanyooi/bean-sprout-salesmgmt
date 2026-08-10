package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.UploadHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UploadHistoryRepository
        extends JpaRepository<UploadHistoryEntity, Long> {

    List<UploadHistoryEntity> findTop50ByOrderByUploadedAtDesc();

    Optional<UploadHistoryEntity> findFirstByRestoredFalseOrderByUploadedAtDesc();
}
