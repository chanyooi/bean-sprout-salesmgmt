송천 문자발송 화면 + 발송완료 관리

변경
1. 거래명세서 PDF · 이미지 · 공유 -> 문자발송
2. 상단 PDF·이미지·공유 -> 문자발송
3. 기존 이미지 공유 기능 유지
4. 명세서 이미지 바로 아래:
   [이번 달 문자 발송완료 N곳]
5. 클릭하면 거래처 / 월 / 발송완료 시각 표시
6. 각 발송완료 기록에 삭제 버튼
7. 삭제는 DB 발송기록도 실제 삭제

기존 공유 성공 후 /statement-send/mark-sent 기록 기능을 그대로 사용합니다.

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-sms-send-management\apply-sms-send-management.ps1

빌드:
.\gradlew.bat clean build -x test
