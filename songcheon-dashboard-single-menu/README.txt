대시보드 모바일 메뉴 버튼 1개 패치

대시보드(/)
- 왼쪽 햄버거 메뉴만 표시
- 오른쪽 중복 햄버거 숨김
- 제목 가운데 정렬 유지

다른 상세 화면
- 왼쪽: 뒤로가기
- 오른쪽: 전체 메뉴
구조를 그대로 유지합니다.

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-dashboard-single-menu\apply-dashboard-single-menu.ps1

빌드:
.\gradlew.bat clean build -x test
