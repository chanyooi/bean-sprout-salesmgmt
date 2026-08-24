package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.ExpenseType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Service
public class MonthlyCostService {

    private final MonthlyExpenseService monthlyExpenseService;
    private final MonthlyCustomExpenseService monthlyCustomExpenseService;

    public MonthlyCostService(
            MonthlyExpenseService monthlyExpenseService,
            MonthlyCustomExpenseService monthlyCustomExpenseService
    ) {
        this.monthlyExpenseService = monthlyExpenseService;
        this.monthlyCustomExpenseService = monthlyCustomExpenseService;
    }

    @Transactional
    public void saveAll(
            YearMonth month,
            Map<ExpenseType, BigDecimal> fixedExpenses,
            List<String> customNames,
            List<BigDecimal> customAmounts
    ) {
        monthlyExpenseService.saveExpenses(month, fixedExpenses);
        monthlyCustomExpenseService.replaceExpenses(month, customNames, customAmounts);
    }
}
