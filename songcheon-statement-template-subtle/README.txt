거래명세서 - 새 템플릿 사용(선택) 영역 정리

변경:
- 파일 선택 전체 박스 크기 축소
- 전체 폭 과도한 강조 제거
- 밝은 회색 배경으로 보조 입력 느낌 강화
- 파일 선택 버튼도 보조 버튼 톤으로 조정
- 설명문 크기/색상 약화
- "선택 사항" 배지 추가
- 기능은 그대로 유지

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-statement-template-subtle\apply-statement-template-subtle.ps1

빌드:
.\gradlew.bat clean build -x test
