문자발송 클릭 즉시 등록 V4

이번 버전은 Windows PowerShell 5.1 호환을 위해
문법을 최대한 단순하게 작성했습니다.

이전 오류 원인:
여러 줄 if 조건 안에서 -or 연산자가 줄 시작에 있어
Windows PowerShell 5.1이 파싱하지 못했습니다.

V4 변경:
- 모든 -or 조건을 한 줄 if 조건으로 작성
- 복잡한 .Replace() 없음
- 한글 문장 치환 없음
- HTML 파일명 고정 안 함
- templates 전체에서 공유 버튼 자동 탐색

동작:
이미지로 바로 공유 클릭
→ 즉시 /statement-send/mark-sent 호출
→ 발송완료 등록
→ 기존 이미지 공유 기능 계속 진행

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-sms-register-on-click-v4\apply-register-on-share-click-v4.ps1

빌드:
.\gradlew.bat clean build -x test
