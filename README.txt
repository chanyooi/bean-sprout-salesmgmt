월 가중평균 콩 원가 패치 사용법

1. 이 폴더 전체를 프로젝트 루트에 복사합니다.
2. patch 폴더 안에는 실제로 덮어쓸 파일을 프로젝트와 같은 상대경로로 넣습니다.
   예:
   patch/src/main/java/com/example/salesmgmt/controller/InventoryController.java
   patch/src/main/java/com/example/salesmgmt/service/MonthlyBeanCostService.java
3. 프로젝트 루트 PowerShell에서 실행:
   powershell -ExecutionPolicy Bypass -File .\monthly-weighted-average-patch\apply-monthly-weighted-average.ps1
4. 기존 파일은 backup-before-monthly-cost-날짜시간 폴더에 자동 백업됩니다.
5. source와 target 경로가 같은 경우는 자동으로 건너뛰므로 자기 자신 덮어쓰기 오류가 발생하지 않습니다.
