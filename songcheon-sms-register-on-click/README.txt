이미지로 바로 공유 버튼 클릭 즉시 발송완료 등록

기존:
버튼 클릭
→ 이미지 생성
→ 공유창
→ 실제 공유 성공
→ markSent()
→ 발송완료 등록

변경:
버튼 클릭
→ markSent()
→ 발송완료 등록
→ 문자 발송완료 표 자동 갱신
→ 이미지 생성
→ 공유창

따라서 공유창에서 취소해도
버튼을 눌렀다면 발송완료 표에 등록됩니다.

중복 방지:
sentMarked 플래그를 추가해서 한 페이지에서 markSent가 두 번 호출돼도
DB에는 한 번만 저장하도록 처리했습니다.

적용:
powershell -ExecutionPolicy Bypass -File .\songcheon-sms-register-on-click\apply-register-on-share-click.ps1

빌드:
.\gradlew.bat clean build -x test
