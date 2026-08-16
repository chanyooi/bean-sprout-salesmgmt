StatementWorkbookService 완전 교체 V2

이번 V2는 Windows PowerShell 5.1 파서 오류를 피하기 위해
스크립트를 단순하게 다시 작성했습니다.

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-statement-workbook-clean-replace-v2\apply-clean-statement-service-v2.ps1

적용 과정:
1. 기존 StatementWorkbookService.java 백업
2. 새 완성본으로 전체 교체
3. returnContainerAmountByDate 문자열이 남았는지 자동 검증
4. 남아 있으면 스크립트가 오류로 중단
5. 없으면 빌드 안내

빌드:
.\gradlew.bat clean build -x test

정상 결과:
[확인] returnContainerAmountByDate 참조 없음
