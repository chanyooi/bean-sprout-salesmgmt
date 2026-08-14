거래명세서 생성 폼 정렬 보정

수정 대상:
- 생성 월
- 새 템플릿 사용 (선택)

변경:
- 두 제목의 위쪽 기준선을 동일하게 맞춤
- 두 입력칸 높이 60px로 동일하게 맞춤
- 생성 월 255px 고정폭 + 템플릿 나머지 폭
- 설명문 위치 및 간격 보정
- 모바일은 세로 배치 유지

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-statement-form-align-fix\apply-statement-form-align-fix.ps1

빌드:
.\gradlew.bat clean build -x test
