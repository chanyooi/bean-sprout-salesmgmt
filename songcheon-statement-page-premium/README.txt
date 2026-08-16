거래명세서 / 문자발송 페이지 프리미엄 스타일 패치

목표
- 조회 영역을 카드처럼 정리
- 버튼 체계를 파란 계열로 통일
- 거래명세서 미리보기 영역을 종이 카드처럼 개선
- 문자 발송완료 영역을 보기 좋게 재정리

적용
powershell -ExecutionPolicy Bypass -File .\songcheon-statement-page-premium\apply-statement-page-premium.ps1

빌드
.\gradlew.bat clean build -x test

주의
- templates 폴더에서 '이미지로 바로 공유', 'PNG 다운로드', 'PDF 다운로드' 중 하나라도 포함한 HTML 파일을 찾아 적용합니다.
- 적용 전 자동으로 .bak 백업 파일을 만듭니다.
