package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.AssociationCreditTransactionEntity;
import com.example.salesmgmt.entity.AssociationCreditTransactionEntity.TransactionType;
import com.example.salesmgmt.repository.AssociationCreditTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AssociationCreditService {

    private final AssociationCreditTransactionRepository repository;

    public AssociationCreditService(
            AssociationCreditTransactionRepository repository
    ) {
        this.repository = repository;
    }

    public BigDecimal getCurrentBalance() {
        return repository.findAll().stream()
                .map(AssociationCreditTransactionEntity::getSignedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public List<AssociationCreditTransactionEntity> getTransactions() {
        return repository.findAllByOrderByTransactionDateDescIdDesc();
    }

    @Transactional
    public AssociationCreditTransactionEntity addTransaction(
            LocalDate date,
            TransactionType type,
            BigDecimal amount,
            String note
    ) {
        return repository.save(
                new AssociationCreditTransactionEntity(date, type, amount, note)
        );
    }

    @Transactional
    public void deleteTransaction(Long id) {
        if (id == null || !repository.existsById(id)) {
            throw new IllegalArgumentException("삭제할 기록을 찾을 수 없습니다.");
        }
        repository.deleteById(id);
    }
}
