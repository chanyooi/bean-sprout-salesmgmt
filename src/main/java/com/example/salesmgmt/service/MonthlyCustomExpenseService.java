package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.MonthlyCustomExpenseEntity;
import com.example.salesmgmt.repository.MonthlyCustomExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MonthlyCustomExpenseService {

    private static final int MAX_CUSTOM_EXPENSES = 30;

    private final MonthlyCustomExpenseRepository monthlyCustomExpenseRepository;

    public MonthlyCustomExpenseService(MonthlyCustomExpenseRepository monthlyCustomExpenseRepository) {
        this.monthlyCustomExpenseRepository = monthlyCustomExpenseRepository;
    }

    @Transactional(readOnly = true)
    public List<CustomExpenseRow> getExpenses(YearMonth month) {
        return monthlyCustomExpenseRepository
                .findAllByMonthStartOrderBySortOrderAscIdAsc(month.atDay(1))
                .stream()
                .map(entity -> new CustomExpenseRow(
                        entity.getId(),
                        entity.getExpenseName(),
                        entity.getAmount()
                ))
                .toList();
    }

    @Transactional
    public void replaceExpenses(
            YearMonth month,
            List<String> names,
            List<BigDecimal> amounts
    ) {
        List<String> safeNames = names == null ? List.of() : names;
        List<BigDecimal> safeAmounts = amounts == null ? List.of() : amounts;

        if (safeNames.size() > MAX_CUSTOM_EXPENSES || safeAmounts.size() > MAX_CUSTOM_EXPENSES) {
            throw new IllegalArgumentException("추가 비용 항목은 최대 30개까지 등록할 수 있습니다.");
        }

        Map<String, BigDecimal> normalized = new LinkedHashMap<>();
        int count = Math.max(safeNames.size(), safeAmounts.size());
        for (int i = 0; i < count; i++) {
            String name = i < safeNames.size() ? safeNames.get(i) : null;
            BigDecimal amount = i < safeAmounts.size() ? safeAmounts.get(i) : BigDecimal.ZERO;

            if (name == null || name.isBlank()) {
                continue;
            }
            if (amount == null) {
                amount = BigDecimal.ZERO;
            }
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("추가 비용은 0원 이상이어야 합니다.");
            }

            String trimmedName = name.trim();
            if (trimmedName.length() > 80) {
                throw new IllegalArgumentException("추가 비용 항목명은 80자 이하로 입력해주세요.");
            }

            // 같은 이름이 여러 번 들어오면 마지막 입력값을 사용한다.
            normalized.put(trimmedName, amount);
        }

        monthlyCustomExpenseRepository.deleteAllByMonthStart(month.atDay(1));

        List<MonthlyCustomExpenseEntity> entities = new ArrayList<>();
        int order = 0;
        for (Map.Entry<String, BigDecimal> entry : normalized.entrySet()) {
            if (entry.getValue().signum() == 0) {
                continue;
            }
            entities.add(new MonthlyCustomExpenseEntity(
                    month.atDay(1),
                    entry.getKey(),
                    entry.getValue(),
                    order++
            ));
        }

        monthlyCustomExpenseRepository.saveAll(entities);
    }

    public record CustomExpenseRow(
            Long id,
            String name,
            BigDecimal amount
    ) {
    }
}
