param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 송천 전체 모바일 UI 적용 ===" -ForegroundColor Cyan
Write-Host "프로젝트: $ProjectRoot"
Write-Host ""

$sourceCss = Join-Path $PSScriptRoot "src\main\resources\static\css\mobile-admin-system.css"
$targetCss = Join-Path $ProjectRoot "src\main\resources\static\css\mobile-admin-system.css"
$templateRoot = Join-Path $ProjectRoot "src\main\resources\templates"

if (-not (Test-Path $sourceCss)) {
    throw "mobile-admin-system.css를 찾을 수 없습니다."
}

if (-not (Test-Path $templateRoot)) {
    throw "templates 폴더를 찾을 수 없습니다: $templateRoot"
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupRoot = Join-Path $ProjectRoot ("backup-before-all-mobile-ui-" + $stamp)
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null

# CSS 설치
$cssDir = Split-Path -Parent $targetCss
New-Item -ItemType Directory -Path $cssDir -Force | Out-Null

if (Test-Path $targetCss) {
    Copy-Item $targetCss (Join-Path $backupRoot "mobile-admin-system.css") -Force
}
Copy-Item $sourceCss $targetCss -Force

$utf8 = New-Object System.Text.UTF8Encoding($false)
$linkLine = '    <link rel="stylesheet" th:href="@{/css/mobile-admin-system.css(v=20260813_1)}">'

$htmlFiles = Get-ChildItem -Path $templateRoot -Recurse -Filter *.html
$changed = 0
$already = 0
$skipped = 0

foreach ($file in $htmlFiles) {
    $content = [System.IO.File]::ReadAllText($file.FullName)

    if ($content -match 'mobile-admin-system\.css') {
        $already++
        continue
    }

    if ($content -notmatch '</head>') {
        Write-Host "[건너뜀] </head> 없음: $($file.FullName)" -ForegroundColor Yellow
        $skipped++
        continue
    }

    # 원본 템플릿 백업 (폴더 구조 유지)
    $relative = $file.FullName.Substring($templateRoot.Length).TrimStart('\')
    $backupFile = Join-Path $backupRoot ("templates\" + $relative)
    $backupDir = Split-Path -Parent $backupFile
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
    Copy-Item $file.FullName $backupFile -Force

    # </head> 바로 앞에 연결. 기존 CSS보다 뒤이므로 모바일 override가 안정적임.
    $newContent = $content -replace '</head>', ($linkLine + "`r`n</head>")
    [System.IO.File]::WriteAllText($file.FullName, $newContent, $utf8)

    Write-Host "[적용] $relative" -ForegroundColor Green
    $changed++
}

Write-Host ""
Write-Host "CSS: src\main\resources\static\css\mobile-admin-system.css" -ForegroundColor Green
Write-Host "변경 템플릿: $changed 개"
Write-Host "이미 적용됨: $already 개"
Write-Host "건너뜀: $skipped 개"
Write-Host "백업: $backupRoot"
Write-Host ""
Write-Host "완료. 다음 명령으로 빌드하세요:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test" -ForegroundColor White
