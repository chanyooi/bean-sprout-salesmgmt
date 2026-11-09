package com.example.salesmgmt.domain;

import java.math.BigDecimal;
import java.util.*;

/**
 * 2025년 귀속 송천콩나물 표준재무제표증명(표준재무상태표/손익계산서/제조원가명세서)
 * 의 계정 코드/순서를 사용한 연간 수기 입력 서식입니다.
 * 공식 홈택스 전자신고 파일 형식은 아닙니다.
 */
public final class StandardStatementSchema {
    private StandardStatementSchema() {}

    public enum Kind {
        BALANCE("표준재무상태표", "자산 · 부채 · 자본"),
        INCOME("표준손익계산서", "매출 · 매출원가 · 판매관리비"),
        COST("표준원가명세서", "제조원가명세서 · 재료비 · 노무비 · 경비");
        private final String label;
        private final String description;
        Kind(String label, String description) { this.label = label; this.description = description; }
        public String getLabel() { return label; }
        public String getDescription() { return description; }
    }

    public record Field(String code, String label, int indent, String formula) {
        public boolean computed() { return formula != null && !formula.isBlank(); }
        public String getCode() { return code; }
        public String getLabel() { return label; }
        public int getIndent() { return indent; }
        public boolean isComputed() { return computed(); }
        public String getFormula() { return formula; }
    }

    private static final Map<Kind, List<Field>> DEFINITIONS = Map.of(
        Kind.BALANCE, parse("""
01|0|Ⅰ. 유동자산|+02 +18
02|1|1. 당좌자산|+03 +04 +05 +06 +10 +11 +12 +16 +17
03|2|(1) 현금 및 현금성자산
04|2|(2) 단기금융상품
05|2|(3) 단기투자증권
06|2|(4) 단기대여금|+07 +08 +09
07|3|① 관계회사
08|3|② 임원 및 종업원
09|3|③ 기타
10|2|(5) 매출채권
11|2|(6) 선급금
12|2|(7) 미수금|+13 +14 +15
13|3|① 공사미수금
14|3|② 분양미수금
15|3|③ 기타
16|2|(8) 선급비용
17|2|(9) 기타
18|1|2. 재고자산|+19..28
19|2|(1) 상품
20|2|(2) 제품
21|2|(3) 반제품 및 재공품
22|2|(4) 원재료
23|2|(5) 부재료
24|2|(6) 미착상품(미착재료)
25|2|(7) 건설용지
26|2|(8) 완성건물
27|2|(9) 미성공사
28|2|(10) 기타
29|0|Ⅱ. 비유동자산|+30 +38 +50 +55
30|1|1. 투자자산|+31 +32 +33 +37
31|2|(1) 장기금융상품
32|2|(2) 장기투자증권
33|2|(3) 장기대여금|+34 +35 +36
34|3|① 관계회사
35|3|② 임원 및 종업원
36|3|③ 기타
37|2|(4) 기타
38|1|2. 유형자산|+39..49
39|2|(1) 토지
40|2|(2) 건물
41|2|(3) 구축물(시설장치 포함)
42|2|(4) 기계장치
43|2|(5) 선박
44|2|(6) 건설용 장비
45|2|(7) 차량운반구
46|2|(8) 공구 및 기구
47|2|(9) 비품
48|2|(10) 건설 중인 자산
49|2|(11) 기타
50|1|3. 무형자산|+51..54
51|2|(1) 영업권
52|2|(2) 산업재산권
53|2|(3) 개발비
54|2|(4) 기타
55|1|4. 기타 비유동자산|+56..61
56|2|(1) 장기매출채권
57|2|(2) 장기선급금
58|2|(3) 장기미수금
59|2|(4) 임차보증금
60|2|(5) 기타보증금
61|2|(6) 기타
62|0|자산총계 (Ⅰ+Ⅱ)|+01 +29
63|0|Ⅰ. 유동부채|+64..72
64|1|1. 단기차입금
65|1|2. 매입채무
66|1|3. 선수금
67|1|4. 미지급금
68|1|5. 예수금
69|1|6. 미지급비용
70|1|7. 유동성 장기부채
71|1|8. 유동성충당부채
72|1|9. 기타
73|0|Ⅱ. 비유동부채|+74 +78..86
74|1|1. 장기차입금|+75 +76 +77
75|2|① 관계회사
76|2|② 임원 및 종업원
77|2|③ 기타
78|1|2. 장기매입채무
79|1|3. 장기선수금
80|1|4. 장기미지급금
81|1|5. 임대보증금
82|1|6. 기타보증금
83|1|7. 퇴직급여충당부채
84|1|8. 기타충당부채
85|1|9. 제준비금
86|1|10. 기타
87|0|부채총계 (Ⅰ+Ⅱ)|+63 +73
88|0|Ⅲ. 자본금
89|0|Ⅳ. 당기순이익|+INCOME:99
90|0|자본총계 (Ⅲ+Ⅳ)|+88 +89
91|0|부채 및 자본총계|+87 +90
"""),
        Kind.INCOME, parse("""
01|0|Ⅰ. 매출액|+02..08
02|1|1. 상품매출
03|1|2. 제품매출
04|1|3. 공사수입
05|1|4. 분양수입
06|1|5. 임대수입
07|1|6. 서비스수입
08|1|7. 기타
09|0|Ⅱ. 매출원가|+10 +15
10|1|1. 상품매출원가|+11 +12 -13 -14
11|2|① 기초재고액
12|2|② 당기매입액
13|2|③ 기말재고액
14|2|④ 타계정대체액
15|1|2. 제조·공사·분양·기타원가|+16 +17 -18 -19
16|2|① 기초재고액
17|2|② 당기총원가|+COST:42
18|2|③ 기말재고액
19|2|④ 타계정대체액
20|0|Ⅲ. 매출총이익 (Ⅰ-Ⅱ)|+01 -09
21|0|Ⅳ. 판매비 및 관리비|+22..39 +42..57
22|1|1. 급여와임금·제수당
23|1|2. 일용급여
24|1|3. 퇴직급여
25|1|4. 복리후생비
26|1|5. 여비교통비
27|1|6. 임차료
28|1|7. 통신비
29|1|8. 전력비
30|1|9. 가스·수도비
31|1|10. 유류비
32|1|11. 보험료
33|1|12. 리스료
34|1|13. 세금과공과
35|1|14. 감가상각비
36|1|15. 무형자산상각비
37|1|16. 수선비
38|1|17. 건물관리비
39|1|18. 기업업무추진비|+40 +41
40|2|① 해외기업업무추진비
41|2|② 국내기업업무추진비
42|1|19. 광고선전비
43|1|20. 도서인쇄비
44|1|21. 운반비
45|1|22. 차량유지비
46|1|23. 교육훈련비
47|1|24. 지급수수료
48|1|25. 판매수수료
49|1|26. 대손상각비
50|1|27. 경상개발비
51|1|28. 소모품비
52|1|29. 의약품비
53|1|30. 의약소모품비
54|1|31. 경영위탁수수료
55|1|32. 외주용역비
56|1|33. 인적용역비
57|1|34. 기타 소계|+58..61
58|2|① 기타1
59|2|② 기타2
60|2|③ 기타3
61|2|④ 기타4
62|0|Ⅴ. 영업손익 (Ⅲ-Ⅳ)|+20 -21
63|0|Ⅵ. 영업외수익|+64..75 +76
64|1|1. 이자수익
65|1|2. 배당수익
66|1|3. 외환차익
67|1|4. 외화환산이익
68|1|5. 단기투자자산 처분이익
69|1|6. 투자자산 처분이익
70|1|7. 유·무형자산 처분이익
71|1|8. 판매장려금
72|1|9. 국고보조금
73|1|10. 보험차익
74|1|11. 충당금·준비금 환입액
75|1|12. 전기오류수정이익
76|1|13. 기타 소계|+77..80
77|2|① 기타1
78|2|② 기타2
79|2|③ 기타3
80|2|④ 기타4
81|0|Ⅶ. 영업외비용|+82..93 +94
82|1|1. 이자비용
83|1|2. 외환차손
84|1|3. 외화환산손실
85|1|4. 기타 대손상각비
86|1|5. 기부금
87|1|6. 단기투자자산 처분손실
88|1|7. 투자자산 처분손실
89|1|8. 유·무형자산 처분손실
90|1|9. 재고자산 감모손실
91|1|10. 재해손실
92|1|11. 충당금·준비금 전입액
93|1|12. 전기오류수정손실
94|1|13. 기타 소계|+95..98
95|2|① 기타1
96|2|② 기타2
97|2|③ 기타3
98|2|④ 기타4
99|0|Ⅷ. 당기순손익 (Ⅴ+Ⅵ-Ⅶ)|+62 +63 -81
"""),
        Kind.COST, parse("""
01|0|Ⅰ. 재료비|+02 +03 -04 -05
02|1|1. 기초재료재고액
03|1|2. 당기재료매입액
04|1|3. 기말재료재고액
05|1|4. 타계정대체액
06|0|Ⅱ. 노무비|+07..10
07|1|1. 급여와임금·제수당
08|1|2. 일용급여
09|1|3. 퇴직급여
10|1|4. 기타
11|0|Ⅲ. 경비|+12..32
12|1|1. 전력비
13|1|2. 가스·수도·유류비
14|1|3. 운반비
15|1|4. 감가상각비
16|1|5. 무형자산상각비
17|1|6. 수선비
18|1|7. 소모품비
19|1|8. 세금과공과
20|1|9. 임차료
21|1|10. 보험료
22|1|11. 복리후생비
23|1|12. 여비교통비
24|1|13. 통신비
25|1|14. 기업업무추진비
26|1|15. 차량유지비
27|1|16. 외주가공비
28|1|17. 지급수수료
29|1|18. 도서인쇄비
30|1|19. 경상개발비
31|1|20. 포장비
32|1|21. 기타 소계|+33..36
33|2|① 기타1
34|2|② 기타2
35|2|③ 기타3
36|2|④ 기타4
37|0|Ⅳ. 당기총제조비용 (Ⅰ+Ⅱ+Ⅲ)|+01 +06 +11
38|0|Ⅴ. 기초재공품원가
39|0|Ⅵ. 합계 (Ⅳ+Ⅴ)|+37 +38
40|0|Ⅶ. 기말재공품원가
41|0|Ⅷ. 타계정대체액
42|0|Ⅸ. 당기제품제조원가 (Ⅵ-Ⅶ-Ⅷ)|+39 -40 -41
""")
    );

    private static List<Field> parse(String data) {
        List<Field> results = new ArrayList<>();
        for (String line : data.split("\\R")) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\|", 4);
            if (parts.length < 3) throw new IllegalArgumentException("Missing standard statement schema field");
            results.add(new Field(parts[0], parts[2], Integer.parseInt(parts[1]),
                    parts.length == 4 ? parts[3].trim() : ""));
        }
        return List.copyOf(results);
    }

    public static List<Field> fields(Kind kind) { return DEFINITIONS.get(kind); }

    public static Field find(Kind kind, String code) {
        return fields(kind).stream().filter(f -> f.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 표준재무제표 코드입니다: " + code));
    }

    public static String key(Kind kind, String code) { return kind.name() + ":" + code; }

    public static Map<String, BigDecimal> evaluate(Map<String, BigDecimal> manual) {
        Map<String, BigDecimal> values = new HashMap<>();
        for (Kind kind : Kind.values()) {
            for (Field f : fields(kind)) compute(kind, f.code(), manual, values, new HashSet<>());
        }
        return values;
    }

    private static BigDecimal compute(Kind kind, String code, Map<String, BigDecimal> manual,
                                      Map<String, BigDecimal> values, Set<String> visiting) {
        String key = key(kind, code);
        BigDecimal cached = values.get(key);
        if (cached != null) return cached;
        if (!visiting.add(key)) throw new IllegalStateException("회계 항목의 순환 참조가 있습니다.");
        Field field = find(kind, code);
        BigDecimal result = BigDecimal.ZERO;
        if (!field.computed()) {
            result = manual.getOrDefault(key, BigDecimal.ZERO);
        } else {
            for (String token : field.formula().split(" +")) {
                int sign = token.charAt(0) == '-' ? -1 : 1;
                String ref = token.substring(1);
                Kind source = kind;
                if (ref.contains(":")) {
                    String[] pair = ref.split(":", 2);
                    source = Kind.valueOf(pair[0]);
                    ref = pair[1];
                }
                if (ref.contains("..")) {
                    String[] bounds = ref.split("\\.\\.", 2);
                    for (int i = Integer.parseInt(bounds[0]); i <= Integer.parseInt(bounds[1]); i++)
                        result = result.add(compute(source, String.format("%02d", i), manual, values, visiting)
                                .multiply(BigDecimal.valueOf(sign)));
                } else {
                    result = result.add(compute(source, ref, manual, values, visiting)
                            .multiply(BigDecimal.valueOf(sign)));
                }
            }
        }
        visiting.remove(key);
        values.put(key, result);
        return result;
    }
}
