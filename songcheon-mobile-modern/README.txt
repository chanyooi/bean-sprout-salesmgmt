송천 모바일 UI 2026

목적
- 데스크톱 화면은 최대한 유지
- 화면 폭 768px 이하에서만 모바일 UI를 현대적으로 개선
- 기존 Java/DB 로직 변경 없음

주요 변화
- 페이지 상단을 부드러운 glass card 형태로 정리
- KPI/요약카드를 2열 Bento grid로 변경
- 버튼/입력창 터치 영역 확대
- 모바일에서 긴 상단 링크를 가로 스크롤 chip 형태로 변경
- 콩 재고 탭 sticky 처리
- 긴 표는 첫 열 고정 + 부드러운 가로스크롤
- 일별 매출 캘린더 모바일 최적화
- 거래명세서 이미지 공유 버튼을 하단 sticky action panel 형태로 개선
- iPhone safe area 대응
- 화면 폭 390px 이하 추가 보정

적용방법
1. 이 폴더(songcheon-mobile-modern)를 salesmgmt 프로젝트 루트에 넣습니다.
2. PowerShell:
   powershell -ExecutionPolicy Bypass -File .\songcheon-mobile-modern\apply-mobile-modern.ps1
3. 서버 재실행 후 모바일에서 확인합니다.

왜 src를 바로 덮어쓰지 않나요?
현재 app.css 전체 내용을 바꾸면 기존 데스크톱 디자인이 사라질 수 있어서,
기존 app.css는 그대로 보존하고 @import 한 줄만 안전하게 추가합니다.
mobile-modern.css 자체는 src 안으로 복사됩니다.

원복
프로젝트 루트에 자동 생성되는
backup-before-mobile-ui-날짜시간\app.css
를 원래 위치로 다시 복사하면 됩니다.
