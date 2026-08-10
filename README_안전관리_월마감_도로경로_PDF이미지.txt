통합 업데이트
- 업로드 이력 + 최근 업로드 복구
- 전체 DB 백업 ZIP 다운로드
- 월 마감 / 마감 해제
- 네이버 실제 도로 배송경로
- 명세서 PDF / PNG 다운로드

1. 업로드 이력·복구
주소:
http://localhost:8080/admin/safety

장부를 "검사 후 DB 저장"할 때마다:
- 파일명
- 업로드 일시
- 신규 주문
- 신규 품목
- 수정 품목
- 삭제 품목
- 삭제 주문
- 동일 건너뜀
을 저장합니다.

업로드 직전 판매자료 전체 스냅샷도 같이 저장합니다.

복구:
- 안전을 위해 가장 최근 미복구 업로드 1건만 복구 가능
- "직전 상태로 복구"를 누르면 그 업로드 직전의 sales_orders / sales_items 상태로 돌아감
- 거래처/단가/재고/입금/코스 정보는 건드리지 않음

2. DB 백업
/admin/safety 에서
"DB 백업 ZIP 다운로드"

ZIP 안 backup.json:
현재 MySQL의 모든 테이블과 행을 저장합니다.
장기 보관용 백업입니다.

3. 월 마감
/admin/safety

예:
2026-07 마감

마감 후 차단:
- input_data 재업로드로 7월 판매 수정/삭제
- /sales에서 7월 수량/단가 수정
- /sales에서 7월 품목 삭제
- /sales에서 7월 주문 삭제

마감 해제 후 다시 수정 가능.

4. 네이버 실제 도로 경로
기존 네이버지도 직선 빨간선을
Directions API 실제 도로 경로로 자동 교체합니다.

중요:
Client Secret은 코드에 넣지 않았습니다.

Windows PowerShell에서 한 번 설정:
[System.Environment]::SetEnvironmentVariable(
  "NAVER_MAP_CLIENT_SECRET",
  "여기에_본인_Client_Secret",
  "User"
)

Client ID도 환경변수로 넣고 싶다면:
[System.Environment]::SetEnvironmentVariable(
  "NAVER_MAP_CLIENT_ID",
  "iwii3ygty2",
  "User"
)

설정 후 IntelliJ를 완전히 종료하고 다시 실행해야 새 환경변수가 반영됩니다.

네이버 Cloud Application:
- Web Dynamic Map
- Geocoding
- Directions 15
모두 활성화 필요.

현재 공식 Maps Directions API 인증:
x-ncp-apigw-api-key-id = Client ID
x-ncp-apigw-api-key = Client Secret

경로는 10분간 서버 메모리에 캐시해서 페이지를 계속 눌러도 API 호출을 과도하게 반복하지 않게 했습니다.

5. 명세서 PDF / PNG
주소:
http://localhost:8080/statement-export

- 월 선택
- 거래처 선택
- 웹 명세서 표시
- PNG 다운로드
- PDF 다운로드

PDF/PNG는 브라우저에서 html2canvas + jsPDF로 생성합니다.
인터넷 연결이 필요합니다.

주의:
이 화면은 "카톡 전송용 간단 명세서"입니다.
기존 template.xlsx 기반 엑셀 명세서는 /statements 에서 그대로 사용합니다.

적용:
1. 서버 Ctrl+C 종료
2. 압축의 src 폴더를 F:\bean_sprout\salesmgmt 에 덮어쓰기
3. 실행
   cd F:\bean_sprout\salesmgmt
   .\gradlew.bat clean bootRun
4. 브라우저 Ctrl+F5

새 DB 테이블:
- upload_histories
- monthly_closures

ddl-auto:update로 자동 생성됩니다.

application.yml 변경 없음
build.gradle 변경 없음
기존 데이터 삭제 없음
