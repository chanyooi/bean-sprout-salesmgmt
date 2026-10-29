package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    @Query("""
            select payment
            from PaymentEntity payment
            join fetch payment.vendor vendor
            where payment.settlementMonth = :settlementMonth
            order by payment.paymentDate desc, payment.id desc
            """)
    List<PaymentEntity> findForSettlementMonth(
            @Param("settlementMonth") String settlementMonth
    );
    @org.springframework.data.jpa.repository.Query("select p from PaymentEntity p join fetch p.vendor where p.paymentDate between :fromDate and :toDate")
    java.util.List<com.example.salesmgmt.entity.PaymentEntity> findForPaymentDateRange(
            @org.springframework.data.repository.query.Param("fromDate") java.time.LocalDate fromDate,
            @org.springframework.data.repository.query.Param("toDate") java.time.LocalDate toDate);
}
