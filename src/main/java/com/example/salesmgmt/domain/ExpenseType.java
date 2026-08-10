package com.example.salesmgmt.domain;

public enum ExpenseType {
    VINYL("비닐"),
    BOX("박스"),
    EMPLOYEE_1_WAGE("직원 1 월급"),
    EMPLOYEE_2_WAGE("직원 2 월급"),
    MEAL("식비"),
    RENT("공장 월세"),
    OTHER("기타 비용");

    private final String label;

    ExpenseType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
