문자발송 클릭 즉시 등록 V2

이 버전은 PowerShell 5.1 파서 오류를 피하기 위해
기존 statement_export.html의 한글 문장이나 JavaScript 코드를
Replace()로 수정하지 않습니다.

하는 일:
1. sms-register-on-click-v2.js 복사
2. statement_export.html에 script 한 줄만 추가
3. 이미지로 바로 공유 버튼 클릭을 capture 단계에서 감지
4. 클릭 즉시 /statement-send/mark-sent 저장
5. 기존 공유 성공 후 동일 거래처/월 markSent 요청은 중복 방지

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-sms-register-on-click-v2\apply-register-on-share-click-v2.ps1

빌드:
.\gradlew.bat clean build -x test

주의:
이 기능은 사용자가 공유창에서 취소해도
버튼을 누른 시점에 발송완료로 등록됩니다.
