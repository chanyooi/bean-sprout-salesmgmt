param([string]$ProjectRoot = (Get-Location).Path)

$ErrorActionPreference = "Stop"

$sourceCss = Join-Path $PSScriptRoot "src\main\resources\static\css\desktop-sidebar-large.css"
$targetCss = Join-Path $ProjectRoot "src\main\resources\static\css\desktop-sidebar-large.css"
$dashboard = Join-Path $ProjectRoot "src\main\resources\templates\dashboard.html"

if (-not (Test-Path $dashboard)) {
    throw "dashboard.html을 찾을 수 없습니다."
}

New-Item -ItemType Directory -Path (Split-Path $targetCss -Parent) -Force | Out-Null
Copy-Item $sourceCss $targetCss -Force

$content = [System.IO.File]::ReadAllText($dashboard)
$linkLine = '    <link rel="stylesheet" th:href="@{/css/desktop-sidebar-large.css(v=20260814_1)}">'

if ($content -notmatch 'desktop-sidebar-large\.css') {
    if ($content -match '</head>') {
        $content = $content -replace '</head>', ($linkLine + "`r`n</head>")
        $utf8 = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($dashboard, $content, $utf8)
    }
}

Write-Host "데스크톱 사이드바 확대 적용 완료" -ForegroundColor Green
Write-Host ".\gradlew.bat clean build -x test" -ForegroundColor Cyan
