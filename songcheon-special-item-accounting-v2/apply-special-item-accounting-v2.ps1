param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 특수품목 회계 V2 적용 ===" -ForegroundColor Cyan

$src = Join-Path $PSScriptRoot "src"
$dst = Join-Path $ProjectRoot "src"

$serviceSource = Join-Path $src "main\java\com\example\salesmgmt\service\SpecialItemAccountingService.java"
$controllerSource = Join-Path $src "main\java\com\example\salesmgmt\controller\SpecialItemAccountingController.java"

$serviceTarget = Join-Path $dst "main\java\com\example\salesmgmt\service\SpecialItemAccountingService.java"
$controllerTarget = Join-Path $dst "main\java\com\example\salesmgmt\controller\SpecialItemAccountingController.java"

$cssRoot = Join-Path $dst "main\resources\static\css"
$jsRoot = Join-Path $dst "main\resources\static\js"
$templateRoot = Join-Path $dst "main\resources\templates"

New-Item -ItemType Directory -Path (Split-Path -Parent $serviceTarget) -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path -Parent $controllerTarget) -Force | Out-Null
New-Item -ItemType Directory -Path $cssRoot -Force | Out-Null
New-Item -ItemType Directory -Path $jsRoot -Force | Out-Null

Copy-Item $serviceSource $serviceTarget -Force
Copy-Item $controllerSource $controllerTarget -Force

Copy-Item `
    (Join-Path $src "main\resources\static\css\special-item-accounting-v2.css") `
    (Join-Path $cssRoot "special-item-accounting-v2.css") `
    -Force

Copy-Item `
    (Join-Path $src "main\resources\static\js\special-item-accounting-v2.js") `
    (Join-Path $jsRoot "special-item-accounting-v2.js") `
    -Force

$cssLine = '    <link rel="stylesheet" th:href="@{/css/special-item-accounting-v2.css(v=20260814_2)}">'
$jsLine = '    <script defer th:src="@{/js/special-item-accounting-v2.js(v=20260814_2)}"></script>'

$utf8 = New-Object System.Text.UTF8Encoding($false)

Get-ChildItem -Path $templateRoot -Recurse -Filter *.html | ForEach-Object {
    if ($_.Name -eq "login.html") {
        return
    }

    $content = [System.IO.File]::ReadAllText($_.FullName)
    $original = $content

    if ($content -notmatch 'special-item-accounting-v2\.css') {
        $content = $content -replace '</head>', ($cssLine + "`r`n</head>")
    }

    if ($content -notmatch 'special-item-accounting-v2\.js') {
        $content = $content -replace '</body>', ($jsLine + "`r`n</body>")
    }

    if ($content -ne $original) {
        [System.IO.File]::WriteAllText(
            $_.FullName,
            $content,
            $utf8
        )
    }
}

Write-Host "적용 완료" -ForegroundColor Green
Write-Host "회수통: 일반 판매/매출 반영" -ForegroundColor Green
Write-Host "두부판: 손두부 판 반납 수익(1판당 2,000원)으로 계산" -ForegroundColor Green
Write-Host ""
Write-Host "빌드:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
