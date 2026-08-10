매출 캘린더 화면 깨짐 CSS 핫픽스

증상:
- 요일이 세로로 표시됨
- 날짜가 한 줄에 하나씩 표시됨
- 월매출/평균/주문/거래처 요약 카드가 일반 텍스트처럼 표시됨

원인:
달력 HTML/데이터는 정상인데 달력 전용 CSS가 브라우저에 적용되지 않은 상태였습니다.

수정:
- sales-calendar.css를 별도 파일로 분리
- sales-calendar.html에서 별도 CSS 직접 로드
- URL 버전값을 붙여 브라우저의 기존 CSS 캐시를 우회
- 달력 핵심 grid 규칙에 우선순위를 높여 다른 스타일과 충돌 방지

적용:
1. 서버 종료 Ctrl+C
2. 압축의 src 폴더를
   F:\bean_sprout\salesmgmt
   에 덮어쓰기
3. 실행
   cd F:\bean_sprout\salesmgmt
   .\gradlew.bat clean bootRun
4. /sales-calendar 접속
5. Ctrl+F5

DB/Java 코드 변경 없음.
