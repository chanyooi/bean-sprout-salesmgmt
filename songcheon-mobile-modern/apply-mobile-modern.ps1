
param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 송천 모바일 UI 적용 ===" -ForegroundColor Cyan
Write-Host "프로젝트: $ProjectRoot"

$appCss = Join-Path $ProjectRoot "src\main\resources\static\css\app.css"
$mobileSource = Join-Path $PSScriptRoot "src\main\resources\static\css\mobile-modern.css"
$mobileTarget = Join-Path $ProjectRoot "src\main\resources\static\css\mobile-modern.css"

if (-not (Test-Path $appCss)) {
    throw "app.css를 찾지 못했습니다: $appCss"
}

if (-not (Test-Path $mobileSource)) {
    throw "mobile-modern.css를 찾지 못했습니다."
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupDir = Join-Path $ProjectRoot ("backup-before-mobile-ui-" + $stamp)
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null

Copy-Item $appCss (Join-Path $backupDir "app.css") -Force

$mobileDir = Split-Path -Parent $mobileTarget
New-Item -ItemType Directory -Path $mobileDir -Force | Out-Null
Copy-Item $mobileSource $mobileTarget -Force

$content = [System.IO.File]::ReadAllText($appCss)

$importLine = '@import url("./mobile-modern.css?v=20260812_1");'

if ($content -notmatch 'mobile-modern\.css') {
    # CSS @import must appear before normal rules.
    $newContent = $importLine + "`r`n" + $content
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($appCss, $newContent, $utf8)
    Write-Host "[적용] app.css에 모바일 스타일 연결" -ForegroundColor Green
} else {
    Write-Host "[확인] app.css에 이미 모바일 스타일이 연결되어 있습니다." -ForegroundColor Yellow
}

Write-Host "[적용] mobile-modern.css" -ForegroundColor Green
Write-Host ""
Write-Host "백업: $backupDir"
Write-Host ""
Write-Host "완료. 서버를 재시작하거나 Railway에 배포한 뒤 모바일에서 확인하세요." -ForegroundColor Cyan
