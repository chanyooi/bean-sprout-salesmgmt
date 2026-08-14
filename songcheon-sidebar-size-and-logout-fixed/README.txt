송천 사이드바 크기 + PC 로그아웃 통합 수정

이번 버전은 Windows PowerShell 5.1 호환을 위해
복잡한 여러 줄 .Replace(...) 메서드 호출을 제거했습니다.

변경:
- PC 사이드바 폭 230px
- 메뉴 높이 46px
- 글씨 13px
- 아이콘 16px
- PC 사이드바 하단 로그아웃 추가
- 로그아웃은 POST /logout
- Thymeleaf CSRF 유지
- 모바일에는 영향 없음

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-sidebar-size-and-logout-fixed\apply-sidebar-size-and-logout-fixed.ps1

빌드:
.\gradlew.bat clean build -x test
