V2: Windows PowerShell 5.1 파서 오류 수정본

송천 공통 사이드바 + DB 장부 복구

1. PC 공통 왼쪽 메뉴
- 대시보드
- 주문 업로드
- 거래명세서
- 거래처 관리
- 입금 관리
- 원가·이익
- 재고 관리
- 사용자 관리

모든 Thymeleaf 업무 화면에 동일하게 표시되며
현재 페이지의 메뉴가 파란색으로 활성화됩니다.

모바일에서는 기존 모바일 내비게이션을 유지합니다.

2. 현재까지 장부 다운로드
주문 업로드 페이지에 자동으로
"현재 장부 복구" 영역이 추가됩니다.

정산월 + 기준일을 선택하면
DB에 저장된 거래내역을 엑셀로 다운로드합니다.

파일명 예:
input_data_복구_2026-08-14.xlsx

시트:
- 정리데이터
- 복구안내

정리데이터 컬럼:
주문번호 / 날짜 / 거래처 / 품목 / 수량 / 전달방식 / 비고

주의:
이번 기능은 DB의 정리된 거래 데이터를 안전하게
다시 엑셀로 꺼내는 복구 기능입니다.

원래 아버지가 쓰던 input_data.xlsx에
특수한 셀 배치, 거래처별 가로 열, 수식 등이 있다면
그 원본 모양까지 100% 동일하게 복원하려면
실제 원본 input_data.xlsx 템플릿이 한 번 필요합니다.

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-sidebar-and-ledger-recovery\apply-sidebar-and-recovery.ps1

빌드:
.\gradlew.bat clean build -x test
