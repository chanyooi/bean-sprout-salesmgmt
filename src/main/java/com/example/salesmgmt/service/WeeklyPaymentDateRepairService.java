package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.WeeklyPaymentEntity;
import com.example.salesmgmt.repository.WeeklyPaymentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class WeeklyPaymentDateRepairService implements ApplicationRunner {

    private final WeeklyPaymentRepository weeklyPaymentRepository;

    public WeeklyPaymentDateRepairService(WeeklyPaymentRepository weeklyPaymentRepository) {
        this.weeklyPaymentRepository = weeklyPaymentRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (WeeklyPaymentEntity payment : weeklyPaymentRepository.findAutomaticCompletions()) {
            LocalDate correctedDate = settlementDateForWeek(payment.getWeekStart(), payment.getCreatedAt().toLocalDate());
            if (!correctedDate.equals(payment.getPaymentDate())) {
                payment.correctPaymentDate(correctedDate);
            }
        }
    }

    static LocalDate settlementDateForWeek(LocalDate weekStart, LocalDate referenceDate) {
        LocalDate weekEnd = weekStart.plusDays(6);
        if (referenceDate.isBefore(weekStart)) {
            return weekStart;
        }
        if (referenceDate.isAfter(weekEnd)) {
            return weekEnd;
        }
        return referenceDate;
    }
}
