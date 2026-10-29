package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.PersonalBankTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface PersonalBankTransactionRepository extends JpaRepository<PersonalBankTransactionEntity,Long> {
    boolean existsByFingerprint(String fingerprint);
    List<PersonalBankTransactionEntity> findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(LocalDate from,LocalDate to);
}
