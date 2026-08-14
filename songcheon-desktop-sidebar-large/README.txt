송천 데스크톱 사이드바 확대 패치

변경
- 사이드바 폭 270px
- 메뉴 높이 54px
- 메뉴 글씨 14.5px
- 아이콘 19px
- 브랜드/사용자 영역 확대
- 선택 메뉴 강조 개선
- 모바일에는 영향 없음

적용
powershell -ExecutionPolicy Bypass -File .\songcheon-desktop-sidebar-large\apply-desktop-sidebar-large.ps1

빌드
.\gradlew.bat clean build -x test
