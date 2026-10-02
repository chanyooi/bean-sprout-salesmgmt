package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.DailyEntryLayoutEntity;
import com.example.salesmgmt.repository.DailyEntryLayoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class DailyEntryLayoutService {
    private static final Set<String> PRODUCTS = Set.of("cutKg", "regular", "small", "curly", "boxRegular", "boxCurly", "mungSprout", "returnContainer", "tofu", "tofuPlate");
    private final DailyEntryLayoutRepository repository;
    private final DailyEntryService entries;
    public DailyEntryLayoutService(DailyEntryLayoutRepository repository, DailyEntryService entries) {
        this.repository = repository;
        this.entries = entries;
    }
    public record Layout(List<String> vendorOrder, List<String> hiddenVendors,
                         List<String> productOrder, List<String> hiddenProducts) {}
    @Transactional(readOnly = true)
    public Layout load() {
        return repository.findById(1L).map(e -> new Layout(split(e.getVendorOrder()), split(e.getHiddenVendors()),
                split(e.getProductOrder()), split(e.getHiddenProducts())))
                .orElseGet(() -> new Layout(List.of(), List.of(), List.of(), List.of()));
    }
    @Transactional
    public void save(Layout layout) {
        Set<String> vendors = new HashSet<>();
        for (int i = 1; i <= entries.vendorOrder().size(); i++) vendors.add(String.valueOf(i));
        validate(layout.vendorOrder(), vendors);
        validate(layout.hiddenVendors(), vendors);
        validate(layout.productOrder(), PRODUCTS);
        validate(layout.hiddenProducts(), PRODUCTS);
        if (layout.hiddenVendors().size() == vendors.size() || layout.hiddenProducts().size() == PRODUCTS.size())
            throw new IllegalArgumentException("거래처와 품목은 각각 하나 이상 남겨주세요.");
        DailyEntryLayoutEntity entity = repository.findById(1L).orElseGet(DailyEntryLayoutEntity::new);
        entity.update(String.join(",", layout.vendorOrder()), String.join(",", layout.hiddenVendors()),
                String.join(",", layout.productOrder()), String.join(",", layout.hiddenProducts()));
        repository.save(entity);
    }
    private void validate(List<String> values, Set<String> allowed) {
        if (values == null || values.size() > allowed.size() || new HashSet<>(values).size() != values.size()
                || values.stream().anyMatch(value -> value == null || !allowed.contains(value))) throw new IllegalArgumentException("표 설정이 올바르지 않습니다. 새로고침 후 다시 시도해주세요.");
    }
    private static List<String> split(String value) {
        return value.isBlank() ? List.of() : List.of(value.split(","));
    }
}
