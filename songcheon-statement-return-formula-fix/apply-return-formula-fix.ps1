param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host "" 
Write-Host "=== 거래명세서 회수통 수식 고정값 오류 수정 ===" -ForegroundColor Cyan

$service = Join-Path $ProjectRoot "src\main\java\com\example\salesmgmt\service\StatementWorkbookService.java"

if (-not (Test-Path $service)) {
    throw "StatementWorkbookService.java를 찾을 수 없습니다: $service"
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backup = Join-Path $ProjectRoot ("backup-before-return-formula-fix-" + $stamp + ".java")
Copy-Item $service $backup -Force

$content = [System.IO.File]::ReadAllText($service)
$original = $content

# 1) 회수통 실제 lineAmount 맵 생성 부분 제거
$content = [regex]::Replace(
    $content,
    '(?s)\s*Map<LocalDate, BigDecimal>\s+returnContainerAmountByDate\s*=\s*createReturnContainerAmountByDate\(\s*items\s*\);',
    ''
)

# 2) 날짜별 회수통 수식을 고정 금액으로 바꾸던 호출 블록 제거
$content = [regex]::Replace(
    $content,
    '(?s)\s*BigDecimal\s+returnQuantity\s*=\s*quantities\.get\(\s*"회수통"\s*\);\s*Integer\s+returnColumn\s*=\s*layout\.itemColumns\(\)\.get\(\s*"회수통"\s*\);\s*if\s*\(\s*returnColumn\s*!=\s*null\s*&&\s*returnQuantity\s*!=\s*null\s*&&\s*returnQuantity\.signum\(\)\s*!=\s*0\s*\)\s*\{\s*BigDecimal\s+returnAmount\s*=\s*returnContainerAmountByDate\.getOrDefault\(\s*date,\s*BigDecimal\.ZERO\s*\);\s*applyReturnContainerAmountToFormula\(\s*sheet,\s*row,\s*returnColumn,\s*returnAmount,\s*date,\s*returnQuantity,\s*warnings\s*\);\s*\}',
    ''
)

# 3) helper 메서드 createReturnContainerAmountByDate 제거
$content = [regex]::Replace(
    $content,
    '(?s)\s*private\s+Map<LocalDate, BigDecimal>\s+createReturnContainerAmountByDate\s*\(\s*List<SalesItemEntity>\s+items\s*\)\s*\{.*?\n\s*\}\n\s*\n\s*private\s+void\s+applyReturnContainerAmountToFormula',
    "`r`n`r`n    private void applyReturnContainerAmountToFormula"
)

# 4) applyReturnContainerAmountToFormula ~ appendAmountToFormula 블록 전체 제거
$content = [regex]::Replace(
    $content,
    '(?s)\s*private\s+void\s+applyReturnContainerAmountToFormula\s*\(.*?\n\s*private\s+Map<LocalDate, Map<String, BigDecimal>>\s+createQuantityPivot',
    "`r`n`r`n    private Map<LocalDate, Map<String, BigDecimal>> createQuantityPivot"
)

if ($content -eq $original) {
    throw "수정할 회수통 고정금액 로직을 찾지 못했습니다. 현재 StatementWorkbookService.java가 예상 구조와 다릅니다."
}

$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($service, $content, $utf8)

Write-Host "[완료] 회수통 고정 금액 수식 보정 로직 제거" -ForegroundColor Green
Write-Host "[유지] template.xlsx 원래 수식(-F행*3000 등)을 그대로 사용" -ForegroundColor Green
Write-Host "백업: $backup"
Write-Host ""
Write-Host "이제 빌드하세요:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
