package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.VendorProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VendorProfileRepository
        extends JpaRepository<VendorProfileEntity, Long> {

    Optional<VendorProfileEntity> findByVendor_Id(Long vendorId);
}
