param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 송천 사이드바 크기 + PC 로그아웃 통합 수정 ===" -ForegroundColor Cyan

$srcCss1 = Join-Path $PSScriptRoot "src\main\resources\static\css\sidebar-size-fixed.css"
$srcCss2 = Join-Path $PSScriptRoot "src\main\resources\static\css\pc-logout-fixed.css"
$srcJs   = Join-Path $PSScriptRoot "src\main\resources\static\js\pc-logout-fixed.js"

$dstCssDir = Join-Path $ProjectRoot "src\main\resources\static\css"
$dstJsDir  = Join-Path $ProjectRoot "src\main\resources\static\js"
$templateRoot = Join-Path $ProjectRoot "src\main\resources\templates"

if (-not (Test-Path $templateRoot)) {
    throw "templates 폴더를 찾을 수 없습니다."
}

New-Item -ItemType Directory -Path $dstCssDir -Force | Out-Null
New-Item -ItemType Directory -Path $dstJsDir -Force | Out-Null

Copy-Item $srcCss1 (Join-Path $dstCssDir "sidebar-size-fixed.css") -Force
Copy-Item $srcCss2 (Join-Path $dstCssDir "pc-logout-fixed.css") -Force
Copy-Item $srcJs   (Join-Path $dstJsDir "pc-logout-fixed.js") -Force

$css1 = '    <link rel="stylesheet" th:href="@{/css/sidebar-size-fixed.css(v=20260814_1)}">'
$css2 = '    <link rel="stylesheet" th:href="@{/css/pc-logout-fixed.css(v=20260814_1)}">'
$js1  = '    <script defer th:src="@{/js/pc-logout-fixed.js(v=20260814_1)}"></script>'

$logoutForm = @'
    <form id="songcheonPcLogoutForm"
          th:action="@{/logout}"
          method="post"
          style="display:none"
          aria-hidden="true"></form>
'@

$utf8 = New-Object System.Text.UTF8Encoding($false)
$changed = 0

Get-ChildItem -Path $templateRoot -Recurse -Filter *.html | ForEach-Object {
    if ($_.Name -eq "login.html") {
        return
    }

    $content = [System.IO.File]::ReadAllText($_.FullName)
    $original = $content

    if ($content -notmatch 'sidebar-size-fixed\.css') {
        $content = $content -replace '</head>', ($css1 + "`r`n</head>")
    }

    if ($content -notmatch 'pc-logout-fixed\.css') {
        $content = $content -replace '</head>', ($css2 + "`r`n</head>")
    }

    if ($content -notmatch 'songcheonPcLogoutForm') {
        $content = $content -replace '</body>', ($logoutForm + "`r`n</body>")
    }

    if ($content -notmatch 'pc-logout-fixed\.js') {
        $content = $content -replace '</body>', ($js1 + "`r`n</body>")
    }

    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($_.FullName, $content, $utf8)
        Write-Host "[적용] $($_.Name)" -ForegroundColor Green
        $changed++
    }
}

Write-Host ""
Write-Host "완료" -ForegroundColor Green
Write-Host "수정 HTML: $changed 개"
Write-Host ""
Write-Host "이제 빌드하세요:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
