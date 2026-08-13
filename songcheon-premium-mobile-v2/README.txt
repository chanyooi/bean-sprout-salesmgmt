송천 Premium Mobile V2

이번 버전은 색상만 맞춘 CSS가 아니라 실제 모바일 업무 동선을 바꿉니다.

핵심
- 모든 업무 화면 공통 상단 앱바
- 상세 화면은 자연스러운 뒤로가기
- 어디서든 전체 메뉴 열기
- 하단 고정 5개 주요 메뉴: 홈 / 명세서 / 거래처 / 입금 / 재고
- 자주 쓰는 메뉴는 엄지 영역에 배치
- 카드 간격과 정보 밀도 재조정
- 탭은 segmented control처럼 정리
- 버튼/입력창 50px
- 표는 데이터 밀도는 유지하면서 행간 확대
- 위험 작업은 빨강, 완료는 초록, 핵심 액션은 파랑
- 390~430px 중심
- 640px 초과 데스크톱은 기존 CSS 유지

적용
powershell -ExecutionPolicy Bypass -File .\songcheon-premium-mobile-v2\apply-premium-mobile-v2.ps1

빌드
.\gradlew.bat clean build -x test

스크립트가 프로젝트 안 모든 Thymeleaf HTML에
premium-mobile-v2.css / premium-mobile-v2.js 를 자동 연결합니다.
