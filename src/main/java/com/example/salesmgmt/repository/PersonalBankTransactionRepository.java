package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.PersonalBankTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface PersonalBankTransactionRepository extends JpaRepository<PersonalBankTransactionEntity,Long> {
    boolean existsByFingerprint(String fingerprint);
    @org.springframework.data.jpa.repository.Query("select distinct t.account from PersonalBankTransactionEntity t order by t.account")
    List<String> findDistinctAccounts();
    // Exact nonempty payer name across every account and every imported month; outgoing rows excluded.
    List<PersonalBankTransactionEntity> findByCounterpartyAndIncomingGreaterThan(String counterparty, java.math.BigDecimal amount);
    List<PersonalBankTransactionEntity> findByTransactionDateBetweenOrderByTransactionDateDescIdDesc(LocalDate from,LocalDate to);
}
