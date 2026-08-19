package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.SalesItemEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalesItemRepository extends JpaRepository<SalesItemEntity, Long> {

    boolean existsBySalesOrder_IdAndItemName(Long salesOrderId, String itemName);

    Optional<SalesItemEntity> findBySalesOrder_IdAndItemName(
            Long salesOrderId,
            String itemName
    );

    List<SalesItemEntity> findAllBySalesOrder_Id(Long salesOrderId);
    List<SalesItemEntity> findAllByItemName(String itemName);
    long countBySalesOrder_Id(Long salesOrderId);
    long deleteAllBySalesOrder_Id(Long salesOrderId);

    @Query("""
            select item
            from SalesItemEntity item
            join fetch item.salesOrder salesOrder
            order by salesOrder.id asc, item.id asc
            """)
    List<SalesItemEntity> findAllForBackup();

    @Query("""
            select item
            from SalesItemEntity item
            join fetch item.salesOrder salesOrder
            join fetch salesOrder.vendor vendor
            where vendor.id = :vendorId
              and salesOrder.deliveryDate between :startDate and :endDate
            order by salesOrder.deliveryDate desc, salesOrder.id desc, item.id asc
            """)
    List<SalesItemEntity> findForVendorPeriod(
            @Param("vendorId") Long vendorId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select item
            from SalesItemEntity item
            join fetch item.salesOrder salesOrder
            join fetch salesOrder.vendor vendor
            where vendor.id = :vendorId
              and item.itemName = :itemName
            """)
    List<SalesItemEntity> findAllForVendorItem(
            @Param("vendorId") Long vendorId,
            @Param("itemName") String itemName
    );

    @Query("""
            select item
            from SalesItemEntity item
            join fetch item.salesOrder salesOrder
            join fetch salesOrder.vendor vendor
            order by salesOrder.deliveryDate desc, salesOrder.id desc, item.id desc
            """)
    List<SalesItemEntity> findRecent(Pageable pageable);

    @Query("""
            select item
            from SalesItemEntity item
            join fetch item.salesOrder salesOrder
            join fetch salesOrder.vendor vendor
            where item.unitPrice is null
            """)
    List<SalesItemEntity> findAllWithoutUnitPrice();

    @Query("""
            select item
            from SalesItemEntity item
            join fetch item.salesOrder salesOrder
            join fetch salesOrder.vendor vendor
            where salesOrder.deliveryDate between :startDate and :endDate
            order by salesOrder.deliveryDate asc,
                     vendor.inputName asc,
                     salesOrder.orderNumber asc,
                     item.itemName asc
            """)
    List<SalesItemEntity> findForMonthlyReport(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select item
            from SalesItemEntity item
            join fetch item.salesOrder salesOrder
            join fetch salesOrder.vendor vendor
            where salesOrder.deliveryDate between :startDate and :endDate
              and item.itemName in ('손두부', '두부판')
            """)
    List<SalesItemEntity> findSpecialItemsForMonthlyBilling(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select max(item.salesOrder.deliveryDate)
            from SalesItemEntity item
            """)
    LocalDate findLatestSalesDate();
}
