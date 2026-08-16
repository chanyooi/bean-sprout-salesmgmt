param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== StatementWorkbookService 완전 교체 V2 ===" -ForegroundColor Cyan

$source = Join-Path $PSScriptRoot "src\main\java\com\example\salesmgmt\service\StatementWorkbookService.java"
$target = Join-Path $ProjectRoot "src\main\java\com\example\salesmgmt\service\StatementWorkbookService.java"

if (-not (Test-Path $source)) {
    throw "교체할 StatementWorkbookService.java를 찾을 수 없습니다."
}

$targetDir = Split-Path -Parent $target

if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupName = "StatementWorkbookService.backup-$stamp.java"
$backup = Join-Path $ProjectRoot $backupName

if (Test-Path $target) {
    Copy-Item $target $backup -Force
    Write-Host "기존 파일 백업: $backup" -ForegroundColor Yellow
}

Copy-Item $source $target -Force

Write-Host ""
Write-Host "[완료] StatementWorkbookService.java 전체 교체" -ForegroundColor Green
Write-Host ""

$found = Select-String -Path $target -Pattern "returnContainerAmountByDate" -SimpleMatch

if ($found) {
    Write-Host "[경고] returnContainerAmountByDate 문자열이 아직 남아 있습니다." -ForegroundColor Red
    $found | ForEach-Object {
        Write-Host $_.Line
    }
    throw "교체 결과 검증에 실패했습니다."
}
else {
    Write-Host "[확인] returnContainerAmountByDate 참조 없음" -ForegroundColor Green
}

Write-Host ""
Write-Host "이제 빌드하세요:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
