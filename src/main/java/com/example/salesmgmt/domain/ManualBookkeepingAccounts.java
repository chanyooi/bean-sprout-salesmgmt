package com.example.salesmgmt.domain;

import java.util.List;

/** 분개장에 직접 선택할 수 있는 기본 계정과목 목록. */
public final class ManualBookkeepingAccounts {
    private ManualBookkeepingAccounts() {}
    public static final List<String> ALL = List.of(
            "보통예금", "현금", "외상매출금", "미수금", "원재료", "재고자산",
            "임차보증금", "기계장치", "차량운반구", "감가상각누계액",
            "외상매입금", "미지급금", "사업주자본", "사업주인출",
            "제품매출", "원재료비", "일용직 인건비", "전력비",
            "임차료", "제조 소모품비", "차량유지비", "보험료",
            "복리후생비", "기업업무추진비", "통신비", "수선비",
            "지급수수료", "세금과공과", "기타 경비"
    );
}
