param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = 'Stop'

Write-Host ""
Write-Host "=== 거래명세서 페이지 프리미엄 정리 적용 ===" -ForegroundColor Cyan

$cssSource = Join-Path $PSScriptRoot 'src\main\resources\static\css\statement-send-premium.css'
$jsSource  = Join-Path $PSScriptRoot 'src\main\resources\static\js\statement-send-premium.js'
$templateRoot = Join-Path $ProjectRoot 'src\main\resources\templates'
$cssTarget = Join-Path $ProjectRoot 'src\main\resources\static\css\statement-send-premium.css'
$jsTarget  = Join-Path $ProjectRoot 'src\main\resources\static\js\statement-send-premium.js'

if (-not (Test-Path $templateRoot)) {
    throw 'templates 폴더를 찾을 수 없습니다.'
}

New-Item -ItemType Directory -Force -Path (Split-Path $cssTarget -Parent) | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path $jsTarget -Parent) | Out-Null
Copy-Item $cssSource $cssTarget -Force
Copy-Item $jsSource $jsTarget -Force

Write-Host "[복사] statement-send-premium.css" -ForegroundColor Green
Write-Host "[복사] statement-send-premium.js" -ForegroundColor Green

$htmlFiles = Get-ChildItem -Path $templateRoot -Recurse -Filter *.html
$candidates = @()
foreach ($file in $htmlFiles) {
    $content = [System.IO.File]::ReadAllText($file.FullName)
    if (($content -match '이미지로 바로 공유') -or ($content -match 'PNG 다운로드') -or ($content -match 'PDF 다운로드')) {
        $candidates += $file.FullName
    }
}

if ($candidates.Count -eq 0) {
    throw '거래명세서/문자발송 HTML 파일을 찾지 못했습니다.'
}

$cssLine = '    <link rel="stylesheet" th:href="@{/css/statement-send-premium.css(v=20260817_1)}">'
$jsLine = '    <script defer th:src="@{/js/statement-send-premium.js(v=20260817_1)}"></script>'
$utf8 = New-Object System.Text.UTF8Encoding($false)

foreach ($candidate in $candidates) {
    $content = [System.IO.File]::ReadAllText($candidate)
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $backup = "$candidate.before-statement-premium-$stamp.bak"
    Copy-Item $candidate $backup -Force

    if (($content -notmatch 'statement-send-premium.css') -and ($content -match '</head>')) {
        $content = $content -replace '</head>', ($cssLine + "`r`n</head>")
    }

    if (($content -notmatch 'statement-send-premium.js') -and ($content -match '</body>')) {
        $content = $content -replace '</body>', ($jsLine + "`r`n</body>")
    }

    [System.IO.File]::WriteAllText($candidate, $content, $utf8)
    Write-Host "[적용] $candidate" -ForegroundColor Green
    Write-Host "[백업] $backup"
}

Write-Host ""
Write-Host '완료. 이제 빌드하세요:' -ForegroundColor Cyan
Write-Host '.\gradlew.bat clean build -x test'
