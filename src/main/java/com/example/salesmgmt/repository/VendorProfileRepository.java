package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.VendorProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;

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

    @Modifying
    @Query("""
        update VendorProfileEntity p set p.salesRegion = :region,
          p.latitude = coalesce(:lat, p.latitude), p.longitude = coalesce(:lng, p.longitude)
        where p.vendor.id = :id and ((:address is null and p.address is null) or p.address = :address)
          and (:manual = true or p.salesRegion is null)
        """)
    int saveSalesLocation(@Param("id") Long id, @Param("address") String address,
        @Param("region") String region, @Param("lat") BigDecimal lat, @Param("lng") BigDecimal lng,
        @Param("manual") boolean manual);
}
