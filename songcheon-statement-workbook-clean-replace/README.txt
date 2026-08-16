StatementWorkbookService 깔끔한 완전 교체본

이전 오류:
cannot find symbol
returnContainerAmountByDate

원인:
부분 정규식 패치로 변수 선언은 제거됐지만
일부 참조 코드가 남았습니다.

이번 버전:
StatementWorkbookService.java를 통째로 교체합니다.

중요 변경:
- returnContainerAmountByDate 관련 코드 완전 제거
- applyReturnContainerAmountToFormula 관련 코드 완전 제거
- 고정값 -33000 삽입 로직 완전 제거
- 회수통은 수량 셀에만 기록
- template.xlsx의 원래 -F행*3000 수식 유지
- template1.xlsx용 날짜 헤더 자동 탐색 유지
- 합계 행 자동 탐색 유지

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-statement-workbook-clean-replace\apply-clean-statement-service.ps1

확인:
Select-String -Path .\src\main\java\com\example\salesmgmt\service\StatementWorkbookService.java -Pattern 'returnContainerAmountByDate'

정상이면 아무 결과도 나오지 않습니다.

빌드:
.\gradlew.bat clean build -x test
