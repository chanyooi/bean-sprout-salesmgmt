package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.VendorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorRepository extends JpaRepository<VendorEntity, Long> {

    Optional<VendorEntity> findByInputName(String inputName);

    List<VendorEntity> findAllByOrderByInputNameAsc();
}
