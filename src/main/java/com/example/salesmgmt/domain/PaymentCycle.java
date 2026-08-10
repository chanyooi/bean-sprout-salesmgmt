package com.example.salesmgmt.domain;

public enum PaymentCycle {
    WEEKLY("주 단위"),
    MONTHLY("월 단위"),
    OTHER("기타");

    private final String label;

    PaymentCycle(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
