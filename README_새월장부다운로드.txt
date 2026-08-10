새 월 input_data.xlsx 자동 생성 기능

추가 주소
- http://localhost:8080/input-template

기능
1. 연/월 선택
2. 선택한 달의 실제 날짜 수만큼 시트 자동 생성
   - 31일 월: 31개
   - 30일 월: 30개
   - 평년 2월: 28개
   - 윤년 2월: 29개
3. 시트 이름 자동 변경
   예: 2026-08 -> 20260801 ~ 20260831
4. 주문번호 자동 변경
   예: 첫 거래처 -> 20260801-001
5. 날짜 자동 변경
6. 현재 거래처 목록과 순서는 유지
7. 판매수량/회수통/손두부/두부판/회수통단가/전달방식/비고는 모두 빈칸
8. 기존 장부 업로드 화면 상단에 "새 월 장부 다운로드" 버튼 추가

사용 예
- 2026-08 선택
  -> input_data_2026-08.xlsx 다운로드
  -> 20260801 ~ 20260831 시트 생성

- 2027-02 선택
  -> 28개 시트 생성

- 2028-02 선택
  -> 윤년이므로 29개 시트 생성

원본 기준
- 사용자가 현재 사용 중인 input_data 파일의 거래처 목록/서식을 기준으로
  입력값만 비운 master 파일을 프로젝트 내부에 포함했습니다.
- 파일 위치:
  src/main/resources/templates/input_data_master.xlsx

적용 방법
1. 실행 중인 서버 Ctrl+C
2. 압축을 풀고 src 폴더를 아래에 덮어쓰기
   F:\bean_sprout\salesmgmt
3. 다시 실행
   cd F:\bean_sprout\salesmgmt
   .\gradlew.bat clean bootRun
4. 브라우저 접속
   http://localhost:8080/input-template
5. 월 선택 후 다운로드

설정/DB 변경
- application.yml 변경 없음
- build.gradle 변경 없음
- MySQL 테이블 변경 없음
- 기존 판매/단가/재고 데이터 변경 없음
