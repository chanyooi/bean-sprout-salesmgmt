송천 전체 화면 모바일 디자인 시스템

목표
- 대시보드와 모든 업무 화면의 모바일 디자인을 하나로 통일
- 모바일 390~430px 최적화
- 데스크톱은 기존 디자인을 최대한 유지

모바일 공통 스타일
1. 연한 회색 배경 (#F3F5F8)
2. 흰 카드
3. 파란 포인트 (#246BFD)
4. 카드 radius 12~16px
5. 버튼/입력창 최소 약 46px
6. 2열 KPI/메뉴 카드
7. 모바일 가로 스크롤 가능한 탭
8. 표 행간/터치 크기 개선
9. 입금관리 버튼 및 전체처리 버튼 모바일 배치
10. 거래명세서 공유 액션 모바일 하단 sticky
11. 로그인 화면도 모바일에서는 같은 파란 관리자 앱 톤으로 통일

중요
- 이 CSS는 @media (max-width: 600px) 중심이라 데스크톱에는 거의 영향을 주지 않습니다.
- 적용 스크립트가 현재 프로젝트의 모든 Thymeleaf .html 파일을 찾아
  mobile-admin-system.css 링크를 자동으로 추가합니다.
- 새 화면을 나중에 추가하면 스크립트를 한 번 더 실행하면 됩니다.
- 이미 CSS가 연결된 파일은 중복으로 넣지 않습니다.

적용 방법

1. 이 폴더를 프로젝트 루트에 놓습니다.

예:
F:\bean_sprout\salesmgmt\songcheon-all-pages-mobile-system

2. 프로젝트 루트에서 실행:

powershell -ExecutionPolicy Bypass -File .\songcheon-all-pages-mobile-system\apply-all-mobile-ui.ps1

3. 빌드:

.\gradlew.bat clean build -x test

4. 로컬 확인 후 GitHub/Railway 배포

자동 백업
- 실행할 때 프로젝트 루트에
  backup-before-all-mobile-ui-날짜시간
  폴더를 자동 생성합니다.

Git에 올릴 때
- 실제 적용된 templates 파일들
- src/main/resources/static/css/mobile-admin-system.css
만 올리면 됩니다.

backup-before-* 및 songcheon-all-pages-mobile-system 폴더 자체는
배포용 소스가 아니므로 Git에 추가하지 않아도 됩니다.
