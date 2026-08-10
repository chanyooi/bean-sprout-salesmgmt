package com.example.salesmgmt.domain;

public enum AppUserRole {
    ADMIN("관리자"),
    FATHER("업무용");

    private final String label;

    AppUserRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
