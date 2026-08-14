param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 송천 PC 사이드바 원래 크기 복원 ===" -ForegroundColor Cyan

$sourceCss =
    Join-Path $PSScriptRoot "src\main\resources\static\css\sidebar-normal-size.css"

$targetCss =
    Join-Path $ProjectRoot "src\main\resources\static\css\sidebar-normal-size.css"

$templateRoot =
    Join-Path $ProjectRoot "src\main\resources\templates"

if (-not (Test-Path $templateRoot)) {
    throw "templates 폴더를 찾을 수 없습니다."
}

New-Item `
    -ItemType Directory `
    -Path (Split-Path -Parent $targetCss) `
    -Force | Out-Null

Copy-Item `
    $sourceCss `
    $targetCss `
    -Force

$linkLine =
'    <link rel="stylesheet" th:href="@{/css/sidebar-normal-size.css(v=20260814_1)}">'

$utf8 =
    New-Object System.Text.UTF8Encoding($false)

$changed = 0

$htmlFiles =
    Get-ChildItem `
        -Path $templateRoot `
        -Recurse `
        -Filter *.html

foreach ($file in $htmlFiles) {
    $content =
        [System.IO.File]::ReadAllText(
            $file.FullName
        )

    if (
        $content -match
        'sidebar-normal-size\.css'
    ) {
        continue
    }

    if ($content -match '</head>') {
        $content =
            $content.Replace(
                '</head>',
                $linkLine
                + "`r`n</head>"
            )

        [System.IO.File]::WriteAllText(
            $file.FullName,
            $content,
            $utf8
        )

        $changed++
    }
}

Write-Host "[완료] CSS 적용" -ForegroundColor Green
Write-Host "수정 HTML: $changed 개"
Write-Host ""
Write-Host "빌드:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
