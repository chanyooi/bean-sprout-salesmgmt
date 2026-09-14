package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.VendorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorRepository extends JpaRepository<VendorEntity, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select v from VendorEntity v where v.id = :id")
    Optional<VendorEntity> findForPaymentUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select v from VendorEntity v order by v.id")
    List<VendorEntity> lockAllForPaymentUpdate();

    Optional<VendorEntity> findByInputName(String inputName);

    Optional<VendorEntity> findByOriginalInputName(String originalInputName);

    List<VendorEntity> findAllByOrderByInputNameAsc();
}
