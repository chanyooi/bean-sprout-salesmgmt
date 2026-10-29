package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.PersonalBankCategoryRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PersonalBankCategoryRuleRepository extends JpaRepository<PersonalBankCategoryRuleEntity,Long> {
    Optional<PersonalBankCategoryRuleEntity> findByCounterparty(String counterparty);
}
