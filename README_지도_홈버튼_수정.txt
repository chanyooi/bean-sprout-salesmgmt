지도 표시 + 홈(대시보드) 버튼 수정

1. 지도 깨짐 수정
증상:
- 지도 타일이 화면 여기저기에 떨어져 보임
- 흰 공간이 크게 생김
- 줌 버튼/저작권 표시 등이 제대로 안 보일 수 있음

원인:
Leaflet JavaScript는 실행됐지만 Leaflet 전용 CSS가 정상 적용되지 않은 경우,
지도 타일이 absolute 위치로 배치되지 못해서 생기는 현상입니다.

수정:
- 로컬 leaflet-fix.css 추가
- 외부 Leaflet CSS가 실패해도 지도 핵심 레이아웃이 동작
- 타일 크기 256x256 강제 보정
- 지도 pane / marker / tooltip / zoom control 위치 보정
- 화면 로딩 후 map.invalidateSize() 실행
- 창 크기 변경 시 지도 크기 재계산

2. 홈(대시보드) 버튼
대시보드 자체를 제외한 주요 화면 상단에
"⌂ 홈(대시보드)" 버튼을 항상 표시합니다.

대상:
- 장부 업로드
- 새 월 장부
- 판매내역
- 월매출 현황
- 단가 관리
- 명세서 생성
- 콩 재고
- 원가·이익
- 입금·미수금
- 거래처·배송코스

적용 방법
1. 서버 종료
   Ctrl + C

2. 압축을 풀고 src 폴더를 아래 프로젝트에 덮어쓰기
   F:\bean_sprout\salesmgmt

3. 다시 실행
   cd F:\bean_sprout\salesmgmt
   .\gradlew.bat clean bootRun

4. 브라우저에서 Ctrl + F5로 강력 새로고침
   (기존 CSS가 브라우저 캐시에 남아 있을 수 있으므로 중요)

5. 확인
   http://localhost:8080/vendors
   http://localhost:8080/

DB/설정 변경 없음
- application.yml 변경 없음
- build.gradle 변경 없음
- MySQL 테이블/데이터 변경 없음
