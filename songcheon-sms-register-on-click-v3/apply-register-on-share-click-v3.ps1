param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 문자발송 클릭 즉시 등록 V3 ===" -ForegroundColor Cyan

$sourceJs =
    Join-Path $PSScriptRoot "src\main\resources\static\js\sms-register-on-click-v3.js"

$targetJs =
    Join-Path $ProjectRoot "src\main\resources\static\js\sms-register-on-click-v3.js"

$templateRoot =
    Join-Path $ProjectRoot "src\main\resources\templates"

if (-not (Test-Path $sourceJs)) {
    throw "sms-register-on-click-v3.js 파일을 찾을 수 없습니다."
}

if (-not (Test-Path $templateRoot)) {
    throw "templates 폴더를 찾을 수 없습니다."
}

$targetJsDir =
    Split-Path -Parent $targetJs

New-Item `
    -ItemType Directory `
    -Path $targetJsDir `
    -Force | Out-Null

Copy-Item `
    $sourceJs `
    $targetJs `
    -Force

Write-Host "[복사] sms-register-on-click-v3.js" -ForegroundColor Green

$candidates = @()

Get-ChildItem `
    -Path $templateRoot `
    -Recurse `
    -Filter *.html |
ForEach-Object {

    $content =
        [System.IO.File]::ReadAllText(
            $_.FullName
        )

    if (
        $content -match 'shareStatementBtn'
        -or
        $content -match '이미지로 바로 공유'
    ) {
        $candidates += $_.FullName
    }
}

if ($candidates.Count -eq 0) {
    Write-Host ""
    Write-Host "공유 화면 HTML을 자동으로 찾지 못했습니다." -ForegroundColor Red
    Write-Host "templates 안 HTML 파일 목록:" -ForegroundColor Yellow

    Get-ChildItem `
        -Path $templateRoot `
        -Recurse `
        -Filter *.html |
    ForEach-Object {
        Write-Host $_.FullName
    }

    throw "shareStatementBtn 또는 이미지로 바로 공유가 포함된 HTML이 없습니다."
}

Write-Host ""
Write-Host "찾은 공유 화면:" -ForegroundColor Cyan

foreach ($candidate in $candidates) {
    Write-Host $candidate
}

$scriptLine =
'    <script defer th:src="@{/js/sms-register-on-click-v3.js(v=20260817_3)}"></script>'

$utf8 =
    New-Object System.Text.UTF8Encoding($false)

foreach ($candidate in $candidates) {

    $content =
        [System.IO.File]::ReadAllText(
            $candidate
        )

    if (
        $content -match
        'sms-register-on-click-v3\.js'
    ) {
        Write-Host "[건너뜀] 이미 적용: $candidate" -ForegroundColor Yellow
        continue
    }

    $stamp =
        Get-Date -Format "yyyyMMdd-HHmmss"

    $fileName =
        [System.IO.Path]::GetFileName(
            $candidate
        )

    $backupName =
        $fileName
        + ".before-click-register-"
        + $stamp
        + ".bak"

    $backup =
        Join-Path $ProjectRoot $backupName

    Copy-Item `
        $candidate `
        $backup `
        -Force

    if (
        $content -notmatch '</body>'
    ) {
        throw "HTML에 </body>가 없습니다: $candidate"
    }

    $content =
        $content -replace
        '</body>',
        ($scriptLine + "`r`n</body>")

    [System.IO.File]::WriteAllText(
        $candidate,
        $content,
        $utf8
    )

    Write-Host "[적용] $candidate" -ForegroundColor Green
    Write-Host "[백업] $backup"
}

Write-Host ""
Write-Host "적용 완료" -ForegroundColor Green
Write-Host ""
Write-Host "빌드:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
