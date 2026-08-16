param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 문자발송 클릭 즉시 등록 V4 ===" -ForegroundColor Cyan

$sourceJs = Join-Path $PSScriptRoot "src\main\resources\static\js\sms-register-on-click-v4.js"
$targetJs = Join-Path $ProjectRoot "src\main\resources\static\js\sms-register-on-click-v4.js"
$templateRoot = Join-Path $ProjectRoot "src\main\resources\templates"

if (-not (Test-Path $sourceJs)) {
    throw "sms-register-on-click-v4.js 파일을 찾을 수 없습니다."
}

if (-not (Test-Path $templateRoot)) {
    throw "templates 폴더를 찾을 수 없습니다."
}

$targetJsDir = Split-Path -Parent $targetJs

if (-not (Test-Path $targetJsDir)) {
    New-Item -ItemType Directory -Path $targetJsDir -Force | Out-Null
}

Copy-Item $sourceJs $targetJs -Force

Write-Host "[복사] sms-register-on-click-v4.js" -ForegroundColor Green

$candidates = New-Object System.Collections.Generic.List[string]

$htmlFiles = Get-ChildItem -Path $templateRoot -Recurse -Filter *.html

foreach ($file in $htmlFiles) {
    $content = [System.IO.File]::ReadAllText($file.FullName)

    $hasButtonId = $content -match 'shareStatementBtn'
    $hasButtonText = $content -match '이미지로 바로 공유'

    if ($hasButtonId -or $hasButtonText) {
        $candidates.Add($file.FullName)
    }
}

if ($candidates.Count -eq 0) {
    Write-Host ""
    Write-Host "공유 화면 HTML을 찾지 못했습니다." -ForegroundColor Red
    Write-Host "templates 폴더의 HTML 목록:" -ForegroundColor Yellow

    foreach ($file in $htmlFiles) {
        Write-Host $file.FullName
    }

    throw "공유 버튼이 있는 HTML을 찾지 못했습니다."
}

Write-Host ""
Write-Host "찾은 공유 화면:" -ForegroundColor Cyan

foreach ($candidate in $candidates) {
    Write-Host $candidate
}

$scriptLine = '    <script defer th:src="@{/js/sms-register-on-click-v4.js(v=20260817_4)}"></script>'
$utf8 = New-Object System.Text.UTF8Encoding($false)

foreach ($candidate in $candidates) {
    $content = [System.IO.File]::ReadAllText($candidate)

    if ($content -match 'sms-register-on-click-v4\.js') {
        Write-Host "[건너뜀] 이미 적용: $candidate" -ForegroundColor Yellow
        continue
    }

    if ($content -notmatch '</body>') {
        Write-Host "[건너뜀] </body> 없음: $candidate" -ForegroundColor Yellow
        continue
    }

    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $name = [System.IO.Path]::GetFileName($candidate)
    $backup = Join-Path $ProjectRoot ($name + ".before-sms-v4-" + $stamp + ".bak")

    Copy-Item $candidate $backup -Force

    $newContent = $content -replace '</body>', ($scriptLine + "`r`n</body>")

    [System.IO.File]::WriteAllText(
        $candidate,
        $newContent,
        $utf8
    )

    Write-Host "[적용] $candidate" -ForegroundColor Green
    Write-Host "[백업] $backup"
}

Write-Host ""
Write-Host "적용 완료" -ForegroundColor Green
Write-Host ""
Write-Host "이제 빌드하세요:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
