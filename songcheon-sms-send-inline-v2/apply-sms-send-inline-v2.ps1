param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 문자발송 인라인 발송완료 표 V2 적용 ===" -ForegroundColor Cyan

$src = Join-Path $PSScriptRoot "src"
$dst = Join-Path $ProjectRoot "src"

$controllerDir = Join-Path $dst "main\java\com\example\salesmgmt\controller"
$cssRoot = Join-Path $dst "main\resources\static\css"
$jsRoot = Join-Path $dst "main\resources\static\js"
$templateRoot = Join-Path $dst "main\resources\templates"

New-Item -ItemType Directory -Path $controllerDir -Force | Out-Null
New-Item -ItemType Directory -Path $cssRoot -Force | Out-Null
New-Item -ItemType Directory -Path $jsRoot -Force | Out-Null

Copy-Item `
    (Join-Path $src "main\java\com\example\salesmgmt\controller\StatementSendManagementController.java") `
    (Join-Path $controllerDir "StatementSendManagementController.java") `
    -Force

Copy-Item `
    (Join-Path $src "main\resources\static\css\sms-send-inline-v2.css") `
    (Join-Path $cssRoot "sms-send-inline-v2.css") `
    -Force

Copy-Item `
    (Join-Path $src "main\resources\static\js\sms-send-inline-v2.js") `
    (Join-Path $jsRoot "sms-send-inline-v2.js") `
    -Force

$cssLine = '    <link rel="stylesheet" th:href="@{/css/sms-send-inline-v2.css(v=20260817_2)}">'
$jsLine = '    <script defer th:src="@{/js/sms-send-inline-v2.js(v=20260817_2)}"></script>'

$utf8 =
    New-Object System.Text.UTF8Encoding($false)

$changed = 0

Get-ChildItem -Path $templateRoot -Recurse -Filter *.html | ForEach-Object {
    if ($_.Name -eq "login.html") {
        return
    }

    $content =
        [System.IO.File]::ReadAllText(
            $_.FullName
        )

    $original =
        $content

    if (
        $content -notmatch
        'sms-send-inline-v2\.css'
    ) {
        $content =
            $content -replace
            '</head>',
            ($cssLine + "`r`n</head>")
    }

    if (
        $content -notmatch
        'sms-send-inline-v2\.js'
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

        $changed++
    }
}

Write-Host "적용 완료" -ForegroundColor Green
Write-Host "- 상단 발송 기록 탭 숨김"
Write-Host "- 문자발송 화면 아래 발송완료 표 항상 표시"
Write-Host "- 이미지 공유 성공 후 자동 새로고침"
Write-Host "- 거래처 / 정산월 / 발송시각 / 삭제"
Write-Host ""
Write-Host "빌드:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
