송천 template1.xlsx 호환 패치

문제 원인
기존 StatementWorkbookService는:
- 32행을 품목 헤더
- 33~63행을 날짜 데이터
- 7행을 정산월 표시
로 고정해서 사용했습니다.

새 template1.xlsx는:
- 28행 품목 헤더
- 29~59행 날짜 데이터
- 60행 합계
- A3 부근이 정산기간 표시 영역
구조라 기존 코드와 맞지 않았습니다.

수정 내용
1. "날짜" 헤더 행을 자동 탐색
2. 아래 "합계" 행을 자동 탐색
3. 날짜 데이터 범위를 자동 계산
4. 새 템플릿은 A3에 정산기간 표시
5. 기존 형식(32행 헤더)도 계속 호환
6. 새 template1.xlsx를 기본 template.xlsx로 함께 적용
7. 팔공식품 특수 헤더 대응
   - "일소" -> 회수통으로 처리
   - 손두부
   - 두부판
8. 일반(소)콩나물 등 별칭 처리
9. 회수통은 DB 실제 lineAmount 기준으로 합계 수식 보정

적용
powershell -ExecutionPolicy Bypass -File .\songcheon-template1-compatible\apply-template1-compatible.ps1

빌드
.\gradlew.bat clean build -x test

테스트
1. 서버 실행
2. 거래명세서
3. 2026-08 선택
4. 새 템플릿을 따로 선택하지 않고 다운로드
   -> ZIP에 포함된 template1.xlsx가 기본 template.xlsx로 사용됨

또는 새 템플릿 선택에서 같은 template1.xlsx를 직접 선택해도
동적 행 탐지 방식으로 처리됩니다.
