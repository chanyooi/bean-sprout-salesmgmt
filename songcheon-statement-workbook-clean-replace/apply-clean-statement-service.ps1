param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== StatementWorkbookService 완전 교체 ===" -ForegroundColor Cyan

$source =
    Join-Path $PSScriptRoot "src\main\java\com\example\salesmgmt\service\StatementWorkbookService.java"

$target =
    Join-Path $ProjectRoot "src\main\java\com\example\salesmgmt\service\StatementWorkbookService.java"

if (-not (Test-Path $source)) {
    throw "교체할 StatementWorkbookService.java를 찾을 수 없습니다."
}

if (-not (Test-Path (Split-Path -Parent $target))) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"

if (Test-Path $target) {
    $backup =
        Join-Path $ProjectRoot (
            "StatementWorkbookService.backup-"
            + $stamp
            + ".java"
        )

    Copy-Item $target $backup -Force

    Write-Host "기존 파일 백업: $backup"
}

Copy-Item $source $target -Force

Write-Host "[완료] StatementWorkbookService.java 전체 교체" -ForegroundColor Green
Write-Host ""
Write-Host "확인:" -ForegroundColor Cyan
Write-Host "Select-String -Path .\src\main\java\com\example\salesmgmt\service\StatementWorkbookService.java -Pattern 'returnContainerAmountByDate'"
Write-Host ""
Write-Host "위 명령 결과가 없어야 정상입니다."
Write-Host ""
Write-Host "빌드:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
