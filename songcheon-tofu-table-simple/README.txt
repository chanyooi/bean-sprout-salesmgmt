손두부 표 단순화

기존:
품목 / 판매 수량 / 판매매출 / 팔공 매입원가 / 판 반납 / 총 반영 매출 / 이익

변경:
품목 / 판매 수량 / 판매매출 / 판 반납 / 총 반영 매출 / 이익

주의:
- 팔공 매입원가는 화면에서만 숨김
- 손두부 이익 계산에는 기존 매입원가가 그대로 사용됨

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-tofu-table-simple\apply-tofu-table-simple.ps1

빌드:
.\gradlew.bat clean build -x test
