package com.example.salesmgmt.domain;

import java.util.List;

public final class BeanCatalog {

    public static final List<BeanCombination> ALLOWED_COMBINATIONS = List.of(
            new BeanCombination(BeanType.SMALL, BeanOrigin.CANADA),
            new BeanCombination(BeanType.SMALL, BeanOrigin.CHINA),
            new BeanCombination(BeanType.MEDIUM, BeanOrigin.CANADA),
            new BeanCombination(BeanType.MEDIUM, BeanOrigin.CHINA),
            new BeanCombination(BeanType.LARGE, BeanOrigin.CANADA),
            new BeanCombination(BeanType.LARGE, BeanOrigin.CHINA),
            new BeanCombination(BeanType.MUNG, BeanOrigin.CHINA),
            new BeanCombination(BeanType.MUNG, BeanOrigin.CHILE)
    );

    private BeanCatalog() {
    }

    public static boolean isAllowed(BeanType beanType, BeanOrigin origin) {
        return ALLOWED_COMBINATIONS.stream()
                .anyMatch(combination -> combination.beanType() == beanType
                        && combination.origin() == origin);
    }

    public record BeanCombination(BeanType beanType, BeanOrigin origin) {
        public String label() {
            return beanType.getLabel() + " / " + origin.getLabel();
        }
    }
}
