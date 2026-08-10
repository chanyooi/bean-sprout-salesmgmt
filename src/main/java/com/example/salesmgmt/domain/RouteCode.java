package com.example.salesmgmt.domain;

public enum RouteCode {
    NONE("미지정"),
    A("A코스"),
    B("B코스"),
    KIMCHEON("김천코스");

    private final String label;

    RouteCode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
