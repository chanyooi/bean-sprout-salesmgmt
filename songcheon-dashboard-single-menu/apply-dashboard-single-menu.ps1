param([string]$ProjectRoot = (Get-Location).Path)

$ErrorActionPreference = "Stop"

$sourceCss = Join-Path $PSScriptRoot "src\main\resources\static\css\dashboard-single-menu.css"
$sourceJs  = Join-Path $PSScriptRoot "src\main\resources\static\js\dashboard-single-menu.js"

$targetCss = Join-Path $ProjectRoot "src\main\resources\static\css\dashboard-single-menu.css"
$targetJs  = Join-Path $ProjectRoot "src\main\resources\static\js\dashboard-single-menu.js"

$templateRoot = Join-Path $ProjectRoot "src\main\resources\templates"

New-Item -ItemType Directory -Path (Split-Path $targetCss -Parent) -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path $targetJs -Parent) -Force | Out-Null

Copy-Item $sourceCss $targetCss -Force
Copy-Item $sourceJs $targetJs -Force

$cssLine = '    <link rel="stylesheet" th:href="@{/css/dashboard-single-menu.css(v=20260813_1)}">'
$jsLine  = '    <script defer th:src="@{/js/dashboard-single-menu.js(v=20260813_1)}"></script>'

$utf8 = New-Object System.Text.UTF8Encoding($false)

Get-ChildItem -Path $templateRoot -Recurse -Filter *.html | ForEach-Object {
    $content = [System.IO.File]::ReadAllText($_.FullName)
    $changed = $false

    if ($content -notmatch 'dashboard-single-menu\.css' -and $content -match '</head>') {
        $content = $content -replace '</head>', ($cssLine + "`r`n</head>")
        $changed = $true
    }

    if ($content -notmatch 'dashboard-single-menu\.js' -and $content -match '</body>') {
        $content = $content -replace '</body>', ($jsLine + "`r`n</body>")
        $changed = $true
    }

    if ($changed) {
        [System.IO.File]::WriteAllText($_.FullName, $content, $utf8)
    }
}

Write-Host "대시보드 메뉴 버튼 1개 패치 완료" -ForegroundColor Green
Write-Host ".\gradlew.bat clean build -x test" -ForegroundColor Cyan
