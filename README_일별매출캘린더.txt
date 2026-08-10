일별 매출 캘린더 업데이트

새 주소:
http://localhost:8080/sales-calendar

기능:
- 월별 달력 형태로 일매출 표시
- 날짜별 주문 건수
- 날짜별 거래처 수
- 날짜별 단가 미등록 건수 표시
- 날짜 클릭 시 그날 판매 상세내역 표시
- 월매출 표시
- 매출 발생일 기준 일평균 표시
- 월 주문건수 / 거래처 수 표시
- 이전 달 / 다음 달 이동
- 월 직접 선택
- 대시보드 빠른 메뉴에 "일별 매출 캘린더" 추가

일매출 계산:
- sales_items.line_amount 합계
- 회수통 음수 금액도 그대로 반영
- 단가 미등록(line_amount null)은 합계에서 제외하고 별도 건수 표시

DB 변경:
- 없음
- 새 테이블 없음

적용:
1. 서버 Ctrl+C
2. 압축의 src 폴더를
   F:\bean_sprout\salesmgmt
   에 덮어쓰기
3. 실행
   cd F:\bean_sprout\salesmgmt
   .\gradlew.bat clean bootRun
4. 브라우저 Ctrl+F5
5. 접속
   http://localhost:8080/sales-calendar

기존 기능:
- 월 마감
- 업로드 이력/복구
- DB 백업
- 네이버 실제 도로 경로
- 명세서 PDF/PNG
에 영향 없음.
