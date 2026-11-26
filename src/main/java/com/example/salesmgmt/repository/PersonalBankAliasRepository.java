package com.example.salesmgmt.repository;
import com.example.salesmgmt.entity.PersonalBankAliasEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PersonalBankAliasRepository extends JpaRepository<PersonalBankAliasEntity,Long>{
 java.util.List<PersonalBankAliasEntity> findByVendorIdIn(java.util.Collection<Long> vendorIds);
 Optional<PersonalBankAliasEntity> findByAlias(String alias);
}
