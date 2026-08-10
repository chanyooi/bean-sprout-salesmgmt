월매출 현황 업데이트

1. 실행 중인 서버를 Ctrl+C 또는 빨간 정지 버튼으로 종료합니다.
2. 이 압축파일 안의 src 폴더를 아래 프로젝트에 덮어씁니다.
   F:\bean_sprout\salesmgmt
3. application.yml과 build.gradle은 포함하지 않았으므로 MySQL 비밀번호와 Gradle 설정은 유지됩니다.
4. 터미널에서 실행합니다.
   cd F:\bean_sprout\salesmgmt
   .\gradlew.bat clean bootRun
5. 브라우저에서 아래 주소를 엽니다.
   http://localhost:8080/reports/monthly

추가 기능
- 최근 판매 월을 기본으로 표시
- 월 선택, 이전 달, 다음 달 이동
- 확정 매출, 주문 수, 거래처 수, 전체 수량, 단가 미등록 수
- 거래처별 매출
- 품목별 판매량 및 매출
- 날짜별 매출
- 단가 미등록 품목은 확정 매출에서 제외하고 경고 표시

주의
- '확정 매출'은 unit_price와 line_amount가 모두 저장된 판매만 합산합니다.
- 단가 미등록이 1건 이상이면 /sales 및 /prices에서 먼저 확인해주세요.
