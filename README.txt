송천 모바일 일관형 관리자 UI

이번 ZIP에는 아래가 들어 있습니다.

1) 대시보드 모바일 수정
- 재고부족 KPI 제거
- KPI 3개(월매출 / 예상이익 / 미수금)로 정리

2) 다른 주요 화면 모바일 통일
- 거래처 관리
- 콩 재고·원가 현황
- 콩 사용 등록
- 콩 사용 기록
- 콩 매입 등록
- 콩 매입 기록

디자인 방향
- 연한 회색 배경
- 흰색 카드
- 파란색 포인트
- 12~16px radius
- 큰 터치 영역
- 모바일 390~430px 기준 최적화

변경 파일
- src/main/resources/templates/dashboard.html
- src/main/resources/static/css/dashboard-mobile-390.css
- src/main/resources/static/css/admin-mobile-unified.css
- src/main/resources/templates/inventory.html
- src/main/resources/templates/inventory-usage-form.html
- src/main/resources/templates/inventory-usage-history.html
- src/main/resources/templates/inventory-purchase-form.html
- src/main/resources/templates/inventory-purchase-history.html
- src/main/resources/templates/vendor-management.html

적용 방법
1. 압축 해제
2. 프로젝트 루트의 src 폴더와 병합/덮어쓰기
3. 빌드 확인
   .\gradlew.bat clean build -x test

참고
- 데스크톱 레이아웃은 최대한 유지
- 모바일 폭 600px 이하에서만 새 디자인이 강하게 적용됨
