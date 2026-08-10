orderSnapshots 컴파일 오류 핫픽스

오류:
Cannot resolve method 'orderSnapshots' in 'ExcelImportResult'

원인:
빈 주문 자동삭제 기능에서는 ExcelImportResult에
List<OrderSnapshot> orderSnapshots 필드가 추가되어야 합니다.
프로젝트에 이전 ExcelImportResult.java가 남아 있으면
ExcelImportController의 result.orderSnapshots()를 찾지 못합니다.

적용:
1. 서버 종료
2. 이 압축의 src 폴더를
   F:\bean_sprout\salesmgmt
   에 덮어쓰기
3. IntelliJ에서 Gradle Reload
4. 아래 명령 실행
   cd F:\bean_sprout\salesmgmt
   .\gradlew.bat clean bootRun

DB, application.yml, build.gradle은 변경하지 않습니다.
