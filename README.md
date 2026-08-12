# 콩 재고 → 콩 사용량·원가 중심 화면 패치

현재 GitHub main 브랜치의 `InventoryController`, `BeanInventoryService`, `BeanUsageCostResult` 구조에 맞춘 패치입니다.

## 바뀌는 점

- `/inventory` 화면 제목을 `콩 사용량 · 원가`로 변경
- 남은 재고/재고부족 표를 메인 화면에서 제거
- `오늘 사용한 콩 등록`을 최우선 입력 폼으로 배치
- 콩 매입 등록은 평균 포대단가 계산용으로 유지
- 조회 월별 총 사용 포대, 총 kg, 콩 사용 원가 표시
- 종류/원산지별 사용 포대, 적용 평균단가, 사용 원가 표시
- 기존 `BeanInventoryService.calculateUsageCost(YearMonth)`를 그대로 사용하므로 DB 스키마 변경 없음
- 기존 매입/사용 데이터 그대로 사용

## 적용

압축을 프로젝트 루트에 풀어 `src` 폴더를 덮어쓰거나, 아래 PowerShell 스크립트를 사용하세요.

```powershell
cd F:\bean_sprout\salesmgmt
powershell -ExecutionPolicy Bypass -File .\apply-bean-usage-cost.ps1
.\gradlew.bat clean compileJava
.\gradlew.bat bootRun
```

## 원가 계산 방식

현재 프로젝트의 기존 계산 로직을 그대로 사용합니다.

- 사용일 이전까지 같은 `콩 종류 + 원산지`의 누적 매입금액 / 누적 매입포대 = 평균 포대단가
- 해당 사용기록의 포대 수 × 평균 포대단가 = 사용 원가
- 월간 사용기록을 합쳐 월 콩 원가 계산

사용일 이전에 매입 기록이 하나도 없으면 해당 사용기록은 `단가 계산 불가`로 표시됩니다.
