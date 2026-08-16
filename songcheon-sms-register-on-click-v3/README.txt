문자발송 클릭 즉시 등록 V3

V2 문제:
statement_export.html이라는 파일명을 고정해서 찾았기 때문에
현재 프로젝트의 실제 템플릿 이름과 달라 적용되지 않았습니다.

V3:
templates 폴더 전체에서 아래 중 하나가 포함된 HTML을 자동 검색합니다.

- shareStatementBtn
- 이미지로 바로 공유

따라서 HTML 파일명이 무엇이든 상관없이
실제 문자발송 화면을 찾아 적용합니다.

동작:
이미지로 바로 공유 클릭
→ 즉시 /statement-send/mark-sent 저장
→ 문자 발송완료 표에 자동 반영
→ 기존 이미지 공유 처리 계속 진행

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-sms-register-on-click-v3\apply-register-on-share-click-v3.ps1

빌드:
.\gradlew.bat clean build -x test
