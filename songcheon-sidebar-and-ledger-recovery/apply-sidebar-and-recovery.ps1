param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 송천 공통 사이드바 + 장부 복구 적용 ===" -ForegroundColor Cyan

$sourceRoot =
    Join-Path $PSScriptRoot "src"

$targetRoot =
    Join-Path $ProjectRoot "src"

Copy-Item `
    (Join-Path $sourceRoot "*") `
    $targetRoot `
    -Recurse `
    -Force

$templateRoot =
    Join-Path $ProjectRoot "src\main\resources\templates"

$cssLine =
'    <link rel="stylesheet" th:href="@{/css/global-sidebar-and-recovery.css(v=20260814_1)}">'

$jsLine =
'    <script defer th:src="@{/js/global-sidebar-and-recovery.js(v=20260814_1)}"></script>'

$utf8 =
    New-Object System.Text.UTF8Encoding($false)

$changed = 0

Get-ChildItem `
    -Path $templateRoot `
    -Recurse `
    -Filter *.html |
ForEach-Object {

    $content =
        [System.IO.File]::ReadAllText(
            $_.FullName
        )

    $original =
        $content

    if (
        $content -notmatch
        'global-sidebar-and-recovery\.css'
        -and
        $content -match '</head>'
    ) {
        $content =
            $content -replace
            '</head>',
            ($cssLine + "`r`n</head>")
    }

    if (
        $content -notmatch
        'global-sidebar-and-recovery\.js'
        -and
        $content -match '</body>'
    ) {
        $content =
            $content -replace
            '</body>',
            ($jsLine + "`r`n</body>")
    }

    if ($content -ne $original) {
        [System.IO.File]::WriteAllText(
            $_.FullName,
            $content,
            $utf8
        )

        Write-Host "[적용] $($_.Name)" -ForegroundColor Green
        $changed++
    }
}

Write-Host ""
Write-Host "적용된 HTML: $changed 개" -ForegroundColor Green
Write-Host "다음 명령으로 빌드:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
