package com.example.salesmgmt.domain;

public enum StatementDeliveryMethod {
    UNASSIGNED("미분류"),
    FAX("팩스"),
    MAIL("우편"),
    SMS("문자");

    private final String label;

    StatementDeliveryMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
