package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.ExpenseCategory;
import com.example.salesmgmt.domain.ExpenseType;
import com.example.salesmgmt.entity.MonthlyExpenseItemEntity;
import com.example.salesmgmt.repository.MonthlyExpenseItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class MonthlyExpenseItemService {

    private final MonthlyExpenseItemRepository repository;
    private final MonthlyExpenseService legacyExpenseService;

    public MonthlyExpenseItemService(
            MonthlyExpenseItemRepository repository,
            MonthlyExpenseService legacyExpenseService
    ) {
        this.repository = repository;
        this.legacyExpenseService = legacyExpenseService;
    }

    /**
     * 기존 고정 입력칸 데이터가 있는 월을 처음 열었을 때 새 자유 항목 구조로 한 번 복사한다.
     * 기존 monthly_expenses 데이터는 삭제하지 않아 되돌리기와 과거 호환성을 유지한다.
     */
    @Transactional
    public void initializeFromLegacyIfEmpty(YearMonth month) {
        if (repository.countByMonthStart(month.atDay(1)) > 0) {
            return;
        }

        Map<ExpenseType, BigDecimal> legacy = legacyExpenseService.getExpenses(month);
        List<MonthlyExpenseItemEntity> items = new ArrayList<>();
        items.add(item(month, ExpenseCategory.PACKAGING, "비닐", legacy.get(ExpenseType.VINYL)));
        items.add(item(month, ExpenseCategory.PACKAGING, "박스", legacy.get(ExpenseType.BOX)));
        items.add(item(month, ExpenseCategory.PERSONNEL, "직원 1 월급", legacy.get(ExpenseType.EMPLOYEE_1_WAGE)));
        items.add(item(month, ExpenseCategory.PERSONNEL, "직원 2 월급", legacy.get(ExpenseType.EMPLOYEE_2_WAGE)));
        items.add(item(month, ExpenseCategory.WELFARE, "식비", legacy.get(ExpenseType.MEAL)));
        items.add(item(month, ExpenseCategory.FACILITY, "공장 월세", legacy.get(ExpenseType.RENT)));
        items.add(item(month, ExpenseCategory.OTHER, "기타 비용", legacy.get(ExpenseType.OTHER)));
        repository.saveAll(items);
    }

    @Transactional(readOnly = true)
    public List<ExpenseItemView> getItems(YearMonth month) {
        return repository.findAllByMonthStartOrderByCategoryAscIdAsc(month.atDay(1))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExpenseGroupView> getGroups(YearMonth month) {
        Map<ExpenseCategory, List<ExpenseItemView>> grouped = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategory category : ExpenseCategory.values()) {
            grouped.put(category, new ArrayList<>());
        }

        getItems(month).forEach(item -> grouped.get(item.category()).add(item));

        List<ExpenseGroupView> groups = new ArrayList<>();
        for (ExpenseCategory category : ExpenseCategory.values()) {
            List<ExpenseItemView> items = List.copyOf(grouped.get(category));
            BigDecimal total = items.stream()
                    .map(ExpenseItemView::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            groups.add(new ExpenseGroupView(category, category.getLabel(), total, items));
        }
        return List.copyOf(groups);
    }

    /**
     * 손익 계산에서는 새 항목이 있으면 새 구조를 사용하고,
     * 아직 새 구조로 열어보지 않은 과거 월은 기존 비용 값을 그대로 사용한다.
     */
    @Transactional(readOnly = true)
    public BigDecimal getOperatingExpenseTotal(YearMonth month) {
        List<MonthlyExpenseItemEntity> items =
                repository.findAllByMonthStartOrderByCategoryAscIdAsc(month.atDay(1));
        if (!items.isEmpty()) {
            return items.stream()
                    .map(MonthlyExpenseItemEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        Map<ExpenseType, BigDecimal> legacy = legacyExpenseService.getExpenses(month);
        return visibleLegacyTypes().stream()
                .map(type -> safe(legacy.get(type)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public void updateItems(
            YearMonth month,
            List<Long> itemIds,
            List<String> itemNames,
            List<ExpenseCategory> categories,
            List<BigDecimal> amounts
    ) {
        if (itemIds == null || itemNames == null || categories == null || amounts == null
                || itemIds.size() != itemNames.size()
                || itemIds.size() != categories.size()
                || itemIds.size() != amounts.size()) {
            throw new IllegalArgumentException("비용 항목 입력값이 올바르지 않습니다.");
        }

        for (int index = 0; index < itemIds.size(); index++) {
            MonthlyExpenseItemEntity entity = repository
                    .findByIdAndMonthStart(itemIds.get(index), month.atDay(1))
                    .orElseThrow(() -> new IllegalArgumentException("수정할 비용 항목을 찾을 수 없습니다."));
            entity.update(categories.get(index), itemNames.get(index), amounts.get(index));
        }
    }

    @Transactional
    public void addItem(
            YearMonth month,
            ExpenseCategory category,
            String itemName,
            BigDecimal amount
    ) {
        repository.save(new MonthlyExpenseItemEntity(
                month.atDay(1),
                category,
                itemName,
                amount
        ));
    }

    @Transactional
    public void deleteItem(YearMonth month, Long id) {
        MonthlyExpenseItemEntity entity = repository.findByIdAndMonthStart(id, month.atDay(1))
                .orElseThrow(() -> new IllegalArgumentException("삭제할 비용 항목을 찾을 수 없습니다."));
        repository.delete(entity);
    }

    @Transactional
    public void copyPreviousMonth(YearMonth month) {
        YearMonth previous = month.minusMonths(1);
        initializeFromLegacyIfEmpty(previous);
        List<MonthlyExpenseItemEntity> previousItems =
                repository.findAllByMonthStartOrderByCategoryAscIdAsc(previous.atDay(1));

        if (previousItems.isEmpty()) {
            throw new IllegalArgumentException("지난달에 복사할 비용 항목이 없습니다.");
        }

        repository.deleteAllByMonthStart(month.atDay(1));
        List<MonthlyExpenseItemEntity> copies = previousItems.stream()
                .map(item -> new MonthlyExpenseItemEntity(
                        month.atDay(1),
                        item.getCategory(),
                        item.getItemName(),
                        item.getAmount()
                ))
                .toList();
        repository.saveAll(copies);
    }

    private MonthlyExpenseItemEntity item(
            YearMonth month,
            ExpenseCategory category,
            String name,
            BigDecimal amount
    ) {
        return new MonthlyExpenseItemEntity(month.atDay(1), category, name, safe(amount));
    }

    private ExpenseItemView toView(MonthlyExpenseItemEntity entity) {
        return new ExpenseItemView(
                entity.getId(),
                entity.getCategory(),
                entity.getCategory().getLabel(),
                entity.getItemName(),
                entity.getAmount()
        );
    }

    private List<ExpenseType> visibleLegacyTypes() {
        return List.of(
                ExpenseType.VINYL,
                ExpenseType.BOX,
                ExpenseType.EMPLOYEE_1_WAGE,
                ExpenseType.EMPLOYEE_2_WAGE,
                ExpenseType.MEAL,
                ExpenseType.RENT,
                ExpenseType.OTHER
        );
    }

    private static BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record ExpenseItemView(
            Long id,
            ExpenseCategory category,
            String categoryLabel,
            String itemName,
            BigDecimal amount
    ) {
    }

    public record ExpenseGroupView(
            ExpenseCategory category,
            String label,
            BigDecimal total,
            List<ExpenseItemView> items
    ) {
    }
}
