param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 송천 문자발송 화면 + 발송완료 관리 적용 ===" -ForegroundColor Cyan

$src = Join-Path $PSScriptRoot "src"
$dst = Join-Path $ProjectRoot "src"
$templateRoot = Join-Path $dst "main\resources\templates"
$cssRoot = Join-Path $dst "main\resources\static\css"
$jsRoot = Join-Path $dst "main\resources\static\js"

New-Item -ItemType Directory -Path (Join-Path $dst "main\java\com\example\salesmgmt\controller") -Force | Out-Null
New-Item -ItemType Directory -Path $cssRoot -Force | Out-Null
New-Item -ItemType Directory -Path $jsRoot -Force | Out-Null

Copy-Item (Join-Path $src "main\java\com\example\salesmgmt\controller\StatementSendManagementController.java") (Join-Path $dst "main\java\com\example\salesmgmt\controller\StatementSendManagementController.java") -Force
Copy-Item (Join-Path $src "main\resources\static\css\sms-send-management.css") (Join-Path $cssRoot "sms-send-management.css") -Force
Copy-Item (Join-Path $src "main\resources\static\js\sms-send-management.js") (Join-Path $jsRoot "sms-send-management.js") -Force

$cssLine = '    <link rel="stylesheet" th:href="@{/css/sms-send-management.css(v=20260817_1)}">'
$jsLine = '    <script defer th:src="@{/js/sms-send-management.js(v=20260817_1)}"></script>'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$changed = 0

Get-ChildItem -Path $templateRoot -Recurse -Filter *.html | ForEach-Object {
    if ($_.Name -eq "login.html") { return }

    $content = [System.IO.File]::ReadAllText($_.FullName)
    $original = $content

    if ($content -notmatch 'sms-send-management\.css') {
        $content = $content -replace '</head>', ($cssLine + "`r`n</head>")
    }

    if ($content -notmatch 'sms-send-management\.js') {
        $content = $content -replace '</body>', ($jsLine + "`r`n</body>")
    }

    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($_.FullName, $content, $utf8)
        $changed++
    }
}

Write-Host "적용 완료" -ForegroundColor Green
Write-Host "- PDF·이미지·공유 -> 문자발송"
Write-Host "- 명세서 아래 발송완료 N곳 버튼"
Write-Host "- 발송기록 목록 + 삭제"
Write-Host "- HTML 수정: $changed 개"
Write-Host ""
Write-Host "빌드:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
