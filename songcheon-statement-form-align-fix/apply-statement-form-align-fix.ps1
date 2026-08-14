param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 거래명세서 생성폼 정렬 보정 ===" -ForegroundColor Cyan

$templateRoot = Join-Path $ProjectRoot "src\main\resources\templates"
$cssRoot = Join-Path $ProjectRoot "src\main\resources\static\css"
$jsRoot = Join-Path $ProjectRoot "src\main\resources\static\js"

if (-not (Test-Path $templateRoot)) {
    throw "templates 폴더를 찾을 수 없습니다."
}

New-Item -ItemType Directory -Path $cssRoot -Force | Out-Null
New-Item -ItemType Directory -Path $jsRoot -Force | Out-Null

Copy-Item `
    (Join-Path $PSScriptRoot "src\main\resources\static\css\statement-form-align-fix.css") `
    (Join-Path $cssRoot "statement-form-align-fix.css") `
    -Force

Copy-Item `
    (Join-Path $PSScriptRoot "src\main\resources\static\js\statement-form-align-fix.js") `
    (Join-Path $jsRoot "statement-form-align-fix.js") `
    -Force

$cssLine = '    <link rel="stylesheet" th:href="@{/css/statement-form-align-fix.css(v=20260814_1)}">'
$jsLine = '    <script defer th:src="@{/js/statement-form-align-fix.js(v=20260814_1)}"></script>'

$utf8 = New-Object System.Text.UTF8Encoding($false)
$changed = 0

Get-ChildItem -Path $templateRoot -Recurse -Filter *.html | ForEach-Object {
    $content = [System.IO.File]::ReadAllText($_.FullName)
    $original = $content

    if ($content -notmatch 'statement-form-align-fix\.css') {
        $content = $content -replace '</head>', ($cssLine + "`r`n</head>")
    }

    if ($content -notmatch 'statement-form-align-fix\.js') {
        $content = $content -replace '</body>', ($jsLine + "`r`n</body>")
    }

    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($_.FullName, $content, $utf8)
        $changed++
    }
}

Write-Host "적용 완료" -ForegroundColor Green
Write-Host "수정 HTML: $changed 개"
Write-Host ""
Write-Host "빌드:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
