param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 송천 공통 사이드바 + 장부 복구 적용 V2 ===" -ForegroundColor Cyan
Write-Host "프로젝트: $ProjectRoot"
Write-Host ""

$sourceController = Join-Path $PSScriptRoot "src\main\java\com\example\salesmgmt\controller\InputDataRecoveryController.java"
$sourceCss = Join-Path $PSScriptRoot "src\main\resources\static\css\global-sidebar-and-recovery.css"
$sourceJs = Join-Path $PSScriptRoot "src\main\resources\static\js\global-sidebar-and-recovery.js"

$targetController = Join-Path $ProjectRoot "src\main\java\com\example\salesmgmt\controller\InputDataRecoveryController.java"
$targetCss = Join-Path $ProjectRoot "src\main\resources\static\css\global-sidebar-and-recovery.css"
$targetJs = Join-Path $ProjectRoot "src\main\resources\static\js\global-sidebar-and-recovery.js"
$templateRoot = Join-Path $ProjectRoot "src\main\resources\templates"

if (-not (Test-Path $sourceController)) {
    throw "InputDataRecoveryController.java를 찾을 수 없습니다: $sourceController"
}

if (-not (Test-Path $sourceCss)) {
    throw "global-sidebar-and-recovery.css를 찾을 수 없습니다: $sourceCss"
}

if (-not (Test-Path $sourceJs)) {
    throw "global-sidebar-and-recovery.js를 찾을 수 없습니다: $sourceJs"
}

if (-not (Test-Path $templateRoot)) {
    throw "templates 폴더를 찾을 수 없습니다: $templateRoot"
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupRoot = Join-Path $ProjectRoot ("backup-before-sidebar-recovery-v2-" + $stamp)

New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null

# 기존 대상 파일 백업
$filesToBackup = @(
    $targetController,
    $targetCss,
    $targetJs
)

foreach ($targetFile in $filesToBackup) {
    if (Test-Path $targetFile) {
        $relative = $targetFile.Substring($ProjectRoot.Length).TrimStart('\')
        $backupFile = Join-Path $backupRoot $relative
        $backupDir = Split-Path -Parent $backupFile

        New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
        Copy-Item $targetFile $backupFile -Force
    }
}

# 소스 파일 복사
New-Item -ItemType Directory -Path (Split-Path -Parent $targetController) -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path -Parent $targetCss) -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path -Parent $targetJs) -Force | Out-Null

Copy-Item $sourceController $targetController -Force
Copy-Item $sourceCss $targetCss -Force
Copy-Item $sourceJs $targetJs -Force

Write-Host "[복사] InputDataRecoveryController.java" -ForegroundColor Green
Write-Host "[복사] global-sidebar-and-recovery.css" -ForegroundColor Green
Write-Host "[복사] global-sidebar-and-recovery.js" -ForegroundColor Green

$cssLine = '    <link rel="stylesheet" th:href="@{/css/global-sidebar-and-recovery.css(v=20260814_2)}">'
$jsLine = '    <script defer th:src="@{/js/global-sidebar-and-recovery.js(v=20260814_2)}"></script>'

$utf8 = New-Object System.Text.UTF8Encoding($false)
$changed = 0
$alreadyApplied = 0

$htmlFiles = Get-ChildItem -Path $templateRoot -Recurse -Filter *.html

foreach ($file in $htmlFiles) {
    $content = [System.IO.File]::ReadAllText($file.FullName)
    $original = $content

    $hasCss = $content -match 'global-sidebar-and-recovery\.css'
    $hasJs = $content -match 'global-sidebar-and-recovery\.js'

    if ($hasCss -and $hasJs) {
        $alreadyApplied++
        continue
    }

    $relativeTemplate = $file.FullName.Substring($templateRoot.Length).TrimStart('\')
    $backupTemplate = Join-Path $backupRoot ("templates\" + $relativeTemplate)
    $backupTemplateDir = Split-Path -Parent $backupTemplate

    New-Item -ItemType Directory -Path $backupTemplateDir -Force | Out-Null
    Copy-Item $file.FullName $backupTemplate -Force

    if (-not $hasCss) {
        if ($content -match '</head>') {
            $content = $content.Replace(
                '</head>',
                $cssLine + "`r`n</head>"
            )
        }
    }

    if (-not $hasJs) {
        if ($content -match '</body>') {
            $content = $content.Replace(
                '</body>',
                $jsLine + "`r`n</body>"
            )
        }
    }

    if ($content -ne $original) {
        [System.IO.File]::WriteAllText(
            $file.FullName,
            $content,
            $utf8
        )

        Write-Host "[적용] $relativeTemplate" -ForegroundColor Green
        $changed++
    }
}

Write-Host ""
Write-Host "완료" -ForegroundColor Cyan
Write-Host "변경 HTML: $changed 개"
Write-Host "이미 적용되어 건너뜀: $alreadyApplied 개"
Write-Host "백업: $backupRoot"
Write-Host ""
Write-Host "이제 빌드하세요:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test" -ForegroundColor White
