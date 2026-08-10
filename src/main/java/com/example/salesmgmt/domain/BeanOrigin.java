package com.example.salesmgmt.domain;

public enum BeanOrigin {
    CANADA("캐나다산"),
    CHINA("중국산"),
    CHILE("칠레산");

    private final String label;

    BeanOrigin(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
