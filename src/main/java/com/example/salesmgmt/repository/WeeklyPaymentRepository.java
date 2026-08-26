package com.example.salesmgmt.repository;

import com.example.salesmgmt.entity.WeeklyPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface WeeklyPaymentRepository extends JpaRepository<WeeklyPaymentEntity, Long> {

    @Query("""
            select payment
            from WeeklyPaymentEntity payment
            join fetch payment.vendor vendor
            where payment.weekStart = :weekStart
            order by payment.paymentDate desc, payment.id desc
            """)
    List<WeeklyPaymentEntity> findForWeek(@Param("weekStart") LocalDate weekStart);
}
