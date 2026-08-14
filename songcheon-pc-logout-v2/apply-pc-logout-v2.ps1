param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 송천 PC 로그아웃 V2 적용 ===" -ForegroundColor Cyan

$sourceCss =
    Join-Path $PSScriptRoot "src\main\resources\static\css\pc-logout-v2.css"

$sourceJs =
    Join-Path $PSScriptRoot "src\main\resources\static\js\pc-logout-v2.js"

$targetCss =
    Join-Path $ProjectRoot "src\main\resources\static\css\pc-logout-v2.css"

$targetJs =
    Join-Path $ProjectRoot "src\main\resources\static\js\pc-logout-v2.js"

$templateRoot =
    Join-Path $ProjectRoot "src\main\resources\templates"

if (-not (Test-Path $templateRoot)) {
    throw "templates 폴더를 찾을 수 없습니다."
}

New-Item `
    -ItemType Directory `
    -Path (Split-Path -Parent $targetCss) `
    -Force | Out-Null

New-Item `
    -ItemType Directory `
    -Path (Split-Path -Parent $targetJs) `
    -Force | Out-Null

Copy-Item $sourceCss $targetCss -Force
Copy-Item $sourceJs $targetJs -Force

$cssLine =
'    <link rel="stylesheet" th:href="@{/css/pc-logout-v2.css(v=20260814_1)}">'

$jsLine =
'    <script defer th:src="@{/js/pc-logout-v2.js(v=20260814_1)}"></script>'

$logoutForm =
@'
    <form id="songcheonPcLogoutForm"
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
        'pc-logout-v2\.css'
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
        'songcheonPcLogoutForm'
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
        'pc-logout-v2\.js'
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
Write-Host "완료: PC 사이드바 하단에 로그아웃 버튼 추가" -ForegroundColor Green
Write-Host "수정 HTML: $changed 개"
Write-Host ""
Write-Host "빌드:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
