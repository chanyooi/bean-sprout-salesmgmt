송천 문자발송 인라인 발송완료 관리 V2

변경:
- 별도 "발송 기록" 탭 제거
- "이번 달 문자 발송완료 확인" 접기 버튼 제거
- 명세서 이미지 바로 아래에 발송완료 표 항상 표시

표:
거래처 | 정산월 | 발송완료 시각 | 삭제

자동 등록:
기존 "이미지로 바로 공유"가 성공하면
statement_export의 markSent()가
/statement-send/mark-sent 를 호출합니다.

V2는 그 요청이 성공하는 순간 발송완료 표를 자동으로 새로고침합니다.
따라서 페이지 새로고침 없이 방금 공유한 거래처가 표에 표시됩니다.

삭제:
삭제 버튼을 누르면 DB 발송완료 기록도 삭제합니다.

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-sms-send-inline-v2\apply-sms-send-inline-v2.ps1

빌드:
.\gradlew.bat clean build -x test
