package com.example.salesmgmt.domain;

/** 수기 월별 금액 요약용 계정 (실제 분개장과 별도 관리). */
public enum ManualBookkeepingCategory {
    SALES("제품매출", true),
    MATERIAL("원재료비", false),
    WAGES("일용직 인건비", false),
    ELECTRICITY("전력비", false),
    RENT("임차료", false),
    SUPPLIES("제조 소모품비", false),
    VEHICLE("차량유지비", false),
    INSURANCE("보험료", false),
    WELFARE("복리후생비", false),
    ENTERTAINMENT("기업업무추진비", false),
    TELECOM("통신비", false),
    REPAIRS("수선비", false),
    FEES("지급수수료", false),
    TAXES("세금과공과", false),
    OTHER("기타 경비", false);

    private final String label;
    private final boolean revenue;

    ManualBookkeepingCategory(String label, boolean revenue) {
        this.label = label;
        this.revenue = revenue;
    }

    public String getLabel() { return label; }
    public boolean isRevenue() { return revenue; }
}
