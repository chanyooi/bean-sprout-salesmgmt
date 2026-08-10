김천코스 추가 업데이트

배송코스가 다음 3개로 변경됩니다.
- A코스
- B코스
- 김천코스

추가 내용
- 거래처 관리의 코스 선택에 김천코스 추가
- 지도 상단에 김천코스 버튼 추가
- 김천코스도 방문순서대로 빨간 선 연결
- 거래처 화면에 김천코스 거래처 수 표시
- 대시보드에도 김천코스 거래처 수 표시
- 기존 A/B코스 설정은 그대로 유지

DB 변경
- 새 테이블 없음
- SQL 작업 없음
- vendor_profiles.route_code에 KIMCHEON 값이 저장됩니다.
- 기존 컬럼 길이로 저장 가능하므로 스키마 수정이 필요 없습니다.

적용
1. 서버 종료: Ctrl + C
2. 압축의 src 폴더를 F:\bean_sprout\salesmgmt 에 덮어쓰기
3. 실행:
   cd F:\bean_sprout\salesmgmt
   .\gradlew.bat clean bootRun
4. 브라우저에서 Ctrl + F5
5. http://localhost:8080/vendors 확인
