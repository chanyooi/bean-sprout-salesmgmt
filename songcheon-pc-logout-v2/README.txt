송천 PC 로그아웃 V2

- PC 왼쪽 사이드바 맨 아래 로그아웃 추가
- 현재 프로젝트의 두 사이드바 구조 모두 감지
  1) desktop-global-sidebar
  2) desktop-app-sidebar
- Spring Security POST /logout 사용
- Thymeleaf hidden form으로 CSRF 토큰 유지
- 로그아웃 클릭 시 확인창 표시
- 모바일에는 추가 버튼을 표시하지 않음

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-pc-logout-v2\apply-pc-logout-v2.ps1

빌드:
.\gradlew.bat clean build -x test
