package com.example.salesmgmt.domain;

public enum BeanType {
    SMALL("소립"),
    MEDIUM("중립"),
    LARGE("대립"),
    MUNG("녹두");

    private final String label;

    BeanType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
