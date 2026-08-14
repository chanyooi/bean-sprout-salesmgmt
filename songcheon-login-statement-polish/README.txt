송천 로그인 + 거래명세서 UI 정리

[로그인]
- 파란 네모 안 "송" 로고 제거
- 상단에 "송천" 텍스트만 표시
- 아래 "매출 관리 시스템"
- 기존 연회색 + 흰 카드 + 파란 포인트 유지
- 로그인 기능 / 로그아웃 메시지 / 비밀번호 보기 유지

[거래명세서]
PC 100% 배율 기준
- 상단 버튼 높이와 간격 통일
- 생성 월 / 템플릿 필드 높이 52px
- 폼의 기준선과 여백 정돈
- 체크박스 줄 정렬
- 다운로드 버튼 크기 정리
- 생성 기준 카드 높이/패딩 통일
- 전체 카드 라운드 및 여백 균형 정리
- 모바일 기존 UI는 최대한 유지

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-login-statement-polish\apply-login-statement-polish.ps1

빌드:
.\gradlew.bat clean build -x test
