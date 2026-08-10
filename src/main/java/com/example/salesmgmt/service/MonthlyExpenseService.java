package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.ExpenseType;
import com.example.salesmgmt.entity.MonthlyExpenseEntity;
import com.example.salesmgmt.repository.MonthlyExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Map;

@Service
public class MonthlyExpenseService {

    private final MonthlyExpenseRepository monthlyExpenseRepository;

    public MonthlyExpenseService(MonthlyExpenseRepository monthlyExpenseRepository) {
        this.monthlyExpenseRepository = monthlyExpenseRepository;
    }

    @Transactional(readOnly = true)
    public Map<ExpenseType, BigDecimal> getExpenses(YearMonth month) {
        Map<ExpenseType, BigDecimal> result = new EnumMap<>(ExpenseType.class);
        for (ExpenseType type : ExpenseType.values()) {
            result.put(type, BigDecimal.ZERO);
        }

        monthlyExpenseRepository
                .findAllByMonthStartOrderByExpenseTypeAsc(month.atDay(1))
                .forEach(entity -> result.put(entity.getExpenseType(), entity.getAmount()));

        return result;
    }

    @Transactional
    public void saveExpenses(YearMonth month, Map<ExpenseType, BigDecimal> amounts) {
        for (ExpenseType type : ExpenseType.values()) {
            BigDecimal amount = amounts.getOrDefault(type, BigDecimal.ZERO);
            if (amount == null) {
                amount = BigDecimal.ZERO;
            }

            MonthlyExpenseEntity entity = monthlyExpenseRepository
                    .findByMonthStartAndExpenseType(month.atDay(1), type)
                    .orElseGet(() -> new MonthlyExpenseEntity(
                            month.atDay(1),
                            type,
                            BigDecimal.ZERO
                    ));

            entity.updateAmount(amount);
            monthlyExpenseRepository.save(entity);
        }
    }
}
