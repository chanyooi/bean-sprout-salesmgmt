param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 문자발송 클릭 즉시 등록 V2 ===" -ForegroundColor Cyan

$sourceJs = Join-Path $PSScriptRoot "src\main\resources\static\js\sms-register-on-click-v2.js"
$targetJs = Join-Path $ProjectRoot "src\main\resources\static\js\sms-register-on-click-v2.js"
$template = Join-Path $ProjectRoot "src\main\resources\templates\statement_export.html"

if (-not (Test-Path $sourceJs)) {
    throw "sms-register-on-click-v2.js 파일을 찾을 수 없습니다."
}

if (-not (Test-Path $template)) {
    throw "statement_export.html 파일을 찾을 수 없습니다."
}

$targetJsDir = Split-Path -Parent $targetJs
New-Item -ItemType Directory -Path $targetJsDir -Force | Out-Null
Copy-Item $sourceJs $targetJs -Force

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backup = Join-Path $ProjectRoot "statement_export.before-click-register-$stamp.html"
Copy-Item $template $backup -Force

$content = [System.IO.File]::ReadAllText($template)

if ($content -notmatch 'sms-register-on-click-v2\.js') {
    $scriptLine = '    <script defer th:src="@{/js/sms-register-on-click-v2.js(v=20260817_2)}"></script>'
    $content = $content -replace '</body>', ($scriptLine + "`r`n</body>")

    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($template, $content, $utf8)

    Write-Host "[적용] statement_export.html" -ForegroundColor Green
}
else {
    Write-Host "[건너뜀] 이미 적용되어 있습니다." -ForegroundColor Yellow
}

Write-Host "[복사] sms-register-on-click-v2.js" -ForegroundColor Green
Write-Host "백업: $backup"
Write-Host ""
Write-Host "이제 빌드하세요:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
