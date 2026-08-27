package com.example.salesmgmt.domain;

public enum ExpenseCategory {
    PERSONNEL("인건비", 10),
    FACILITY("시설·공과금", 20),
    PACKAGING("포장·소모품", 30),
    DELIVERY("차량·배송비", 40),
    WELFARE("복리후생", 50),
    OTHER("기타", 60);

    private final String label;
    private final int displayOrder;

    ExpenseCategory(String label, int displayOrder) {
        this.label = label;
        this.displayOrder = displayOrder;
    }

    public String getLabel() {
        return label;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
