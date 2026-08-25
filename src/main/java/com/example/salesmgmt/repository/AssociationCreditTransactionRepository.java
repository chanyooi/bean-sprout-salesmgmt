package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.AssociationCreditTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssociationCreditTransactionRepository
        extends JpaRepository<AssociationCreditTransactionEntity, Long> {

    List<AssociationCreditTransactionEntity> findAllByOrderByTransactionDateDescIdDesc();
}
