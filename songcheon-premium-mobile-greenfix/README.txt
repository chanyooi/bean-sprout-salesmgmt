송천 Premium Mobile 초록 버튼 수정

수정 대상
- 새 월 장부 다운로드
- 수익분석 / 수익 분석

변경
- 초록색 제거
- 대시보드와 동일한 #3182F6 파란 포인트 적용
- 버튼 텍스트 줄바꿈 방지
- 업로드 다운로드 버튼은 모바일에서 한 줄 전체 폭으로 정리

적용
powershell -ExecutionPolicy Bypass -File .\songcheon-premium-mobile-greenfix\apply-green-button-fix.ps1

빌드
.\gradlew.bat clean build -x test
