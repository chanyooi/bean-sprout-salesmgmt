단가 미등록 필터 업데이트

추가 기능
- /sales 화면의 "단가 미등록" 카드 클릭 가능
- 클릭하면 현재 선택 월/거래처에서 단가가 없는 품목만 표시
- 조회 필터에 "단가 상태: 전체 / 단가 미등록만" 추가
- 미등록 상태에서 단가를 입력하고 수정하면 해당 행이 목록에서 자동으로 사라짐
- 이전 달/다음 달 이동 시에도 미등록 필터 유지
- 거래처 필터와 함께 사용 가능
- 전체 판매내역 보기 버튼 제공

적용 방법
1. 서버 Ctrl+C로 종료
2. 압축의 src 폴더를 F:\bean_sprout\salesmgmt 에 덮어쓰기
3. 실행:
   cd F:\bean_sprout\salesmgmt
   .\gradlew.bat clean bootRun
4. http://localhost:8080/sales 접속
5. "단가 미등록" 카드를 클릭

설정/DB 변경 없음
- application.yml 변경 없음
- build.gradle 변경 없음
- DB 테이블 변경 없음
