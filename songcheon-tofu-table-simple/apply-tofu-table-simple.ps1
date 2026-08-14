param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 손두부 표 단순화 적용 ===" -ForegroundColor Cyan

$templateRoot = Join-Path $ProjectRoot "src\main\resources\templates"
$cssRoot = Join-Path $ProjectRoot "src\main\resources\static\css"
$jsRoot = Join-Path $ProjectRoot "src\main\resources\static\js"

if (-not (Test-Path $templateRoot)) {
    throw "templates 폴더를 찾을 수 없습니다."
}

New-Item -ItemType Directory -Path $cssRoot -Force | Out-Null
New-Item -ItemType Directory -Path $jsRoot -Force | Out-Null

Copy-Item `
    (Join-Path $PSScriptRoot "src\main\resources\static\css\tofu-table-simple.css") `
    (Join-Path $cssRoot "tofu-table-simple.css") `
    -Force

Copy-Item `
    (Join-Path $PSScriptRoot "src\main\resources\static\js\tofu-table-simple.js") `
    (Join-Path $jsRoot "tofu-table-simple.js") `
    -Force

$cssLine = '    <link rel="stylesheet" th:href="@{/css/tofu-table-simple.css(v=20260814_1)}">'
$jsLine = '    <script defer th:src="@{/js/tofu-table-simple.js(v=20260814_1)}"></script>'

$utf8 = New-Object System.Text.UTF8Encoding($false)
$changed = 0

Get-ChildItem -Path $templateRoot -Recurse -Filter *.html | ForEach-Object {
    if ($_.Name -eq "login.html") {
        return
    }

    $content = [System.IO.File]::ReadAllText($_.FullName)
    $original = $content

    if ($content -notmatch 'tofu-table-simple\.css') {
        $content = $content -replace '</head>', ($cssLine + "`r`n</head>")
    }

    if ($content -notmatch 'tofu-table-simple\.js') {
        $content = $content -replace '</body>', ($jsLine + "`r`n</body>")
    }

    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($_.FullName, $content, $utf8)
        $changed++
    }
}

Write-Host "적용 완료" -ForegroundColor Green
Write-Host "화면에서는 팔공 매입원가 열만 제거됩니다."
Write-Host "원가/이익 계산 로직은 그대로 유지됩니다."
Write-Host ""
Write-Host "빌드:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
