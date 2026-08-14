param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 송천 Modern Login + PC 로그아웃 적용 ===" -ForegroundColor Cyan
Write-Host "프로젝트: $ProjectRoot"
Write-Host ""

$sourceLogin =
    Join-Path $PSScriptRoot "src\main\resources\templates\login.html"

$sourceLoginCss =
    Join-Path $PSScriptRoot "src\main\resources\static\css\login-modern-blue.css"

$sourceLogoutCss =
    Join-Path $PSScriptRoot "src\main\resources\static\css\desktop-logout.css"

$sourceLogoutJs =
    Join-Path $PSScriptRoot "src\main\resources\static\js\desktop-logout.js"

$targetLogin =
    Join-Path $ProjectRoot "src\main\resources\templates\login.html"

$targetLoginCss =
    Join-Path $ProjectRoot "src\main\resources\static\css\login-modern-blue.css"

$targetLogoutCss =
    Join-Path $ProjectRoot "src\main\resources\static\css\desktop-logout.css"

$targetLogoutJs =
    Join-Path $ProjectRoot "src\main\resources\static\js\desktop-logout.js"

$templateRoot =
    Join-Path $ProjectRoot "src\main\resources\templates"

if (-not (Test-Path $templateRoot)) {
    throw "templates 폴더를 찾을 수 없습니다: $templateRoot"
}

$stamp =
    Get-Date -Format "yyyyMMdd-HHmmss"

$backupRoot =
    Join-Path $ProjectRoot (
        "backup-before-modern-login-logout-" + $stamp
    )

New-Item `
    -ItemType Directory `
    -Path $backupRoot `
    -Force | Out-Null

# login.html 백업
if (Test-Path $targetLogin) {
    $backupLogin =
        Join-Path $backupRoot "login.html"

    Copy-Item `
        $targetLogin `
        $backupLogin `
        -Force
}

# 파일 복사
New-Item `
    -ItemType Directory `
    -Path (Split-Path -Parent $targetLoginCss) `
    -Force | Out-Null

New-Item `
    -ItemType Directory `
    -Path (Split-Path -Parent $targetLogoutJs) `
    -Force | Out-Null

Copy-Item $sourceLogin $targetLogin -Force
Copy-Item $sourceLoginCss $targetLoginCss -Force
Copy-Item $sourceLogoutCss $targetLogoutCss -Force
Copy-Item $sourceLogoutJs $targetLogoutJs -Force

Write-Host "[교체] login.html" -ForegroundColor Green
Write-Host "[복사] login-modern-blue.css" -ForegroundColor Green
Write-Host "[복사] desktop-logout.css" -ForegroundColor Green
Write-Host "[복사] desktop-logout.js" -ForegroundColor Green

$cssLine =
'    <link rel="stylesheet" th:href="@{/css/desktop-logout.css(v=20260814_1)}">'

$jsLine =
'    <script defer th:src="@{/js/desktop-logout.js(v=20260814_1)}"></script>'

$logoutForm =
@'
    <form id="desktopLogoutSecurityForm"
          th:action="@{/logout}"
          method="post"
          style="display:none"
          aria-hidden="true"></form>
'@

$utf8 =
    New-Object System.Text.UTF8Encoding($false)

$changed = 0

$htmlFiles =
    Get-ChildItem `
        -Path $templateRoot `
        -Recurse `
        -Filter *.html

foreach ($file in $htmlFiles) {

    if ($file.Name -eq "login.html") {
        continue
    }

    $content =
        [System.IO.File]::ReadAllText(
            $file.FullName
        )

    $original =
        $content

    if (
        $content -notmatch
        'desktop-logout\.css'
    ) {
        if ($content -match '</head>') {
            $content =
                $content.Replace(
                    '</head>',
                    $cssLine
                    + "`r`n</head>"
                )
        }
    }

    if (
        $content -notmatch
        'desktopLogoutSecurityForm'
    ) {
        if ($content -match '</body>') {
            $content =
                $content.Replace(
                    '</body>',
                    $logoutForm
                    + "`r`n</body>"
                )
        }
    }

    if (
        $content -notmatch
        'desktop-logout\.js'
    ) {
        if ($content -match '</body>') {
            $content =
                $content.Replace(
                    '</body>',
                    $jsLine
                    + "`r`n</body>"
                )
        }
    }

    if ($content -ne $original) {
        [System.IO.File]::WriteAllText(
            $file.FullName,
            $content,
            $utf8
        )

        Write-Host "[적용] $($file.Name)" -ForegroundColor Green
        $changed++
    }
}

Write-Host ""
Write-Host "적용 완료" -ForegroundColor Cyan
Write-Host "수정 HTML: $changed 개"
Write-Host "백업 폴더: $backupRoot"
Write-Host ""
Write-Host "빌드:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
