package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.VendorPriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VendorPriceRepository extends JpaRepository<VendorPriceEntity, Long> {

    Optional<VendorPriceEntity> findByVendor_IdAndItemName(
            Long vendorId,
            String itemName
    );

    List<VendorPriceEntity> findByVendor_IdOrderByItemNameAsc(Long vendorId);

    @Query("""
            select price
            from VendorPriceEntity price
            join fetch price.vendor vendor
            order by vendor.inputName asc, price.itemName asc
            """)
    List<VendorPriceEntity> findAllWithVendor();
}
