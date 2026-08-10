거래처 관리 + A/B코스 지도 + 입금/미수금 + 메인 대시보드 업데이트

추가 기능 1: 거래처 관리 / A·B 배송코스
주소:
http://localhost:8080/vendors

관리 항목
- 거래중 / 중지
- A코스 / B코스 / 미지정
- 방문 순서
- 주소
- 전화번호
- 정산 방식: 주 단위 / 월 단위 / 기타
- 배송 메모
- 지도 위도/경도
- 회수통 보증금 여부 표시
  * 회수통 보증금은 단가관리의 회수통 단가를 기준으로 표시
  * 중복 설정을 만들지 않음

지도
- API 키 필요 없음
- A코스/B코스 버튼으로 전환
- 방문 순서대로 번호 표시
- 거래처 사이를 빨간 선으로 연결
- "지도에서 찍기" 클릭 -> 지도 클릭 -> 위도/경도 입력 -> 저장
- 지도 타일을 불러오려면 PC 인터넷 연결이 필요함
- 거래처 정보와 좌표는 MySQL에 저장됨

추가 기능 2: 입금 / 미수금
주소:
http://localhost:8080/payments

기능
- 정산월 선택
- 거래처별 월 청구액 자동 계산
- 실제 입금 기록
- 입금일
- 입금액
- 메모
- 미수금 자동 계산
- 입금완료 / 일부입금 / 미입금 표시
- 입금 기록 삭제
- 주 단위 거래처는 같은 정산월에 여러 번 입금하면 합산

중요:
실제 입금이 다음 달에 들어와도 정산월을 이전 달로 지정하면
그 이전 달 미수금에서 차감됨.

예:
7월 청구 1,000,000원
8월 3일에 1,000,000원 입금
입금 등록 시 정산월 = 2026-07
-> 7월 미수금 0원

추가 기능 3: 메인 대시보드
주소:
http://localhost:8080/

기존 장부 업로드 주소 변경:
http://localhost:8080/upload

대시보드 표시
- 월매출
- 예상이익
- 미수금
- 단가 미등록
- 재고 부족
- A코스/B코스 거래처 수
- 미수 거래처 목록
- 재고 부족 목록
- 모든 주요 기능 빠른 메뉴

새 DB 테이블
- vendor_profiles
- payments

현재 application.yml의
spring.jpa.hibernate.ddl-auto: update
설정으로 서버 시작 시 자동 생성됨.

기존 테이블과 데이터
- vendors
- sales_orders
- sales_items
- vendor_prices
- 재고/원가 관련 테이블
모두 그대로 유지됨.

적용 방법
1. 서버 종료
   Ctrl + C

2. 압축을 풀고 src 폴더를 아래에 덮어쓰기
   F:\bean_sprout\salesmgmt

3. 다시 실행
   cd F:\bean_sprout\salesmgmt
   .\gradlew.bat clean bootRun

4. 먼저 대시보드 확인
   http://localhost:8080/

5. 거래처 코스 설정
   http://localhost:8080/vendors

6. 입금/미수금
   http://localhost:8080/payments

주의
- application.yml 변경 없음
- build.gradle 변경 없음
- MySQL 비밀번호 변경 없음
- 지도 화면만 OpenStreetMap 타일을 사용하므로 인터넷 연결 필요
- 주소만 입력한다고 자동으로 좌표를 찾지는 않음
  "지도에서 찍기" 기능으로 좌표 저장
