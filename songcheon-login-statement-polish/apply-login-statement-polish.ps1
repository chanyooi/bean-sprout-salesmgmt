param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 송천 로그인 + 거래명세서 정렬 개선 ===" -ForegroundColor Cyan

$templateRoot = Join-Path $ProjectRoot "src\main\resources\templates"
$staticCssRoot = Join-Path $ProjectRoot "src\main\resources\static\css"
$staticJsRoot = Join-Path $ProjectRoot "src\main\resources\static\js"

if (-not (Test-Path $templateRoot)) {
    throw "templates 폴더를 찾을 수 없습니다."
}

New-Item -ItemType Directory -Path $staticCssRoot -Force | Out-Null
New-Item -ItemType Directory -Path $staticJsRoot -Force | Out-Null

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupRoot = Join-Path $ProjectRoot ("backup-before-login-statement-polish-" + $stamp)
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null

# 로그인 HTML 백업 + 교체
$loginTarget = Join-Path $templateRoot "login.html"
$loginSource = Join-Path $PSScriptRoot "src\main\resources\templates\login.html"

if (Test-Path $loginTarget) {
    Copy-Item $loginTarget (Join-Path $backupRoot "login.html") -Force
}
Copy-Item $loginSource $loginTarget -Force

# CSS/JS 복사
Copy-Item `
    (Join-Path $PSScriptRoot "src\main\resources\static\css\login-modern-blue-v2.css") `
    (Join-Path $staticCssRoot "login-modern-blue-v2.css") `
    -Force

Copy-Item `
    (Join-Path $PSScriptRoot "src\main\resources\static\css\statement-polish.css") `
    (Join-Path $staticCssRoot "statement-polish.css") `
    -Force

Copy-Item `
    (Join-Path $PSScriptRoot "src\main\resources\static\js\statement-polish.js") `
    (Join-Path $staticJsRoot "statement-polish.js") `
    -Force

$cssLine = '    <link rel="stylesheet" th:href="@{/css/statement-polish.css(v=20260814_1)}">'
$jsLine = '    <script defer th:src="@{/js/statement-polish.js(v=20260814_1)}"></script>'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$changed = 0

Get-ChildItem -Path $templateRoot -Recurse -Filter *.html | ForEach-Object {
    if ($_.Name -eq "login.html") {
        return
    }

    $content = [System.IO.File]::ReadAllText($_.FullName)
    $original = $content

    if ($content -notmatch 'statement-polish\.css') {
        $content = $content -replace '</head>', ($cssLine + "`r`n</head>")
    }

    if ($content -notmatch 'statement-polish\.js') {
        $content = $content -replace '</body>', ($jsLine + "`r`n</body>")
    }

    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($_.FullName, $content, $utf8)
        $changed++
        Write-Host "[적용] $($_.Name)" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "완료" -ForegroundColor Green
Write-Host "- 로그인: 파란 송 로고 제거 + 송천 텍스트"
Write-Host "- 거래명세서: PC 정렬/높이/간격 통일"
Write-Host "- 수정 HTML: $changed 개"
Write-Host "- 백업: $backupRoot"
Write-Host ""
Write-Host "빌드:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
