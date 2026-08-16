param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== template1.xlsx 호환 거래명세서 적용 ===" -ForegroundColor Cyan

$sourceService = Join-Path $PSScriptRoot "src\main\java\com\example\salesmgmt\service\StatementWorkbookService.java"
$sourceTemplate = Join-Path $PSScriptRoot "src\main\resources\template.xlsx"

$targetService = Join-Path $ProjectRoot "src\main\java\com\example\salesmgmt\service\StatementWorkbookService.java"
$targetTemplate = Join-Path $ProjectRoot "src\main\resources\template.xlsx"

if (-not (Test-Path $sourceService)) {
    throw "StatementWorkbookService.java를 찾을 수 없습니다."
}

if (-not (Test-Path $sourceTemplate)) {
    throw "template.xlsx를 찾을 수 없습니다."
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupRoot = Join-Path $ProjectRoot ("backup-before-template1-" + $stamp)

New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null

if (Test-Path $targetService) {
    Copy-Item $targetService (Join-Path $backupRoot "StatementWorkbookService.java") -Force
}

if (Test-Path $targetTemplate) {
    Copy-Item $targetTemplate (Join-Path $backupRoot "template.xlsx") -Force
}

New-Item -ItemType Directory -Path (Split-Path -Parent $targetService) -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path -Parent $targetTemplate) -Force | Out-Null

Copy-Item $sourceService $targetService -Force
Copy-Item $sourceTemplate $targetTemplate -Force

Write-Host "[교체] StatementWorkbookService.java" -ForegroundColor Green
Write-Host "[교체] src/main/resources/template.xlsx" -ForegroundColor Green
Write-Host ""
Write-Host "백업: $backupRoot"
Write-Host ""
Write-Host "이제 빌드하세요:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
