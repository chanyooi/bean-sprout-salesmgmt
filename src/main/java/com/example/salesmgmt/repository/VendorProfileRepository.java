package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.VendorProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VendorProfileRepository
        extends JpaRepository<VendorProfileEntity, Long> {

    Optional<VendorProfileEntity> findByVendor_Id(Long vendorId);

    @Query("""
            select profile
            from VendorProfileEntity profile
            join fetch profile.vendor vendor
            """)
    List<VendorProfileEntity> findAllWithVendor();
}
