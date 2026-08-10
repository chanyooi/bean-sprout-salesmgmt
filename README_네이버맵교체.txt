네이버맵 교체 업데이트

사용 Client ID
- iwii3ygty2
- 이 값은 Web Dynamic Map에서 브라우저에 노출되는 Client ID입니다.
- Client Secret은 코드에 넣지 않았습니다.

이번 변경
1. Leaflet / OpenStreetMap 제거
2. NAVER Maps JavaScript API v3 적용
3. A코스 / B코스 / 김천코스 버튼 유지
4. 거래처 번호 마커 + 거래처명 라벨 표시
5. 방문순서대로 빨간 선 연결
6. 네이버 Geocoder로 주소 -> 좌표 검색
7. 지도에서 직접 위치 클릭 가능
8. 기존 vendor_profiles의 위도/경도 그대로 사용
9. 홈(대시보드) 버튼 유지

네이버 클라우드 Application에서 반드시 확인할 것
- Web Dynamic Map 활성화
- Geocoding 활성화
- Web 서비스 URL:
  http://localhost:8080

사용 순서
1. 거래처 코스 선택
2. 방문순서 입력
3. 주소 입력
4. "주소로 위치찾기" 클릭
5. 네이버 지도에서 위치 확인
6. 해당 거래처 행 "저장" 클릭

빨간 선
- 같은 코스에 좌표가 2곳 이상 있으면 표시
- 현재 버전은 거래처 사이를 직선으로 연결
- 실제 도로를 따라가는 경로는 Directions 15 + Client Secret을
  서버 환경변수로 넣는 다음 단계에서 추가 가능

적용
1. 서버 종료: Ctrl + C
2. 압축의 src 폴더를 F:\bean_sprout\salesmgmt 에 덮어쓰기
3. 실행:
   cd F:\bean_sprout\salesmgmt
   .\gradlew.bat clean bootRun
4. 브라우저 Ctrl + F5
5. http://localhost:8080/vendors 확인

DB 변경 없음.
application.yml 변경 없음.
build.gradle 변경 없음.
