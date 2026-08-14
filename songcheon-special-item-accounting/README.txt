송천 특수품목 회계 분리

손두부
- 팔공식품: 매입원가
- 아포농협: 재판매 매출
- 이익 = 아포농협 판매매출 - 팔공 매입원가

두부판
- 일반 판매매출에서 제외
- 보증금·회수 항목으로 분리

회수통
- 일반 판매매출에서 제외
- 보증금·회수 항목으로 분리

월매출
- 자체 판매품목 + 아포농협 손두부 판매만 반영

품목별 판매
- 손두부/두부판/회수통 행을 일반 품목표에서 숨기고
  아래에 재판매 / 보증금·회수 카드로 분리

예상이익
- 기존 예상이익 표시가 있는 화면은 특수품목 회계 보정값을 적용

주의
- 손두부 거래처 판별은 statementName에 '팔공', '아포농협'이 포함되는지 기준
- 다른 거래처명으로 저장되어 있다면 README의 기준을 맞춰야 함

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-special-item-accounting\apply-special-item-accounting.ps1

빌드:
.\gradlew.bat clean build -x test
