param([string]$ProjectRoot = (Get-Location).Path)
$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 송천 Premium Mobile V2 적용 ===" -ForegroundColor Cyan

$sourceCss = Join-Path $PSScriptRoot "src\main\resources\static\css\premium-mobile-v2.css"
$sourceJs  = Join-Path $PSScriptRoot "src\main\resources\static\js\premium-mobile-v2.js"
$targetCss = Join-Path $ProjectRoot "src\main\resources\static\css\premium-mobile-v2.css"
$targetJs  = Join-Path $ProjectRoot "src\main\resources\static\js\premium-mobile-v2.js"
$templateRoot = Join-Path $ProjectRoot "src\main\resources\templates"

if (-not (Test-Path $templateRoot)) { throw "templates 폴더를 찾을 수 없습니다." }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupRoot = Join-Path $ProjectRoot ("backup-before-premium-mobile-v2-" + $stamp)
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null

New-Item -ItemType Directory -Path (Split-Path $targetCss -Parent) -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path $targetJs -Parent) -Force | Out-Null
Copy-Item $sourceCss $targetCss -Force
Copy-Item $sourceJs $targetJs -Force

$cssLine = '    <link rel="stylesheet" th:href="@{/css/premium-mobile-v2.css(v=20260813_1)}">'
$jsLine  = '    <script defer th:src="@{/js/premium-mobile-v2.js(v=20260813_1)}"></script>'
$utf8 = New-Object System.Text.UTF8Encoding($false)

$changed = 0
Get-ChildItem -Path $templateRoot -Recurse -Filter *.html | ForEach-Object {
    $content = [System.IO.File]::ReadAllText($_.FullName)
    $original = $content
    $relative = $_.FullName.Substring($templateRoot.Length).TrimStart('\')

    if ($content -notmatch 'premium-mobile-v2\.css' -and $content -match '</head>') {
        $content = $content -replace '</head>', ($cssLine + "`r`n</head>")
    }

    if ($content -notmatch 'premium-mobile-v2\.js' -and $content -match '</body>') {
        $content = $content -replace '</body>', ($jsLine + "`r`n</body>")
    }

    if ($content -ne $original) {
        $backupFile = Join-Path $backupRoot ("templates\" + $relative)
        New-Item -ItemType Directory -Path (Split-Path $backupFile -Parent) -Force | Out-Null
        Copy-Item $_.FullName $backupFile -Force
        [System.IO.File]::WriteAllText($_.FullName, $content, $utf8)
        Write-Host "[적용] $relative" -ForegroundColor Green
        $changed++
    }
}

Write-Host ""
Write-Host "적용 완료: $changed 개 템플릿" -ForegroundColor Green
Write-Host "백업: $backupRoot"
Write-Host "다음: .\gradlew.bat clean build -x test" -ForegroundColor Cyan
