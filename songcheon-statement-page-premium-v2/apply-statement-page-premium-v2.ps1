param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== APPLY STATEMENT PAGE PREMIUM V2 ===" -ForegroundColor Cyan

$sourceCss = Join-Path $PSScriptRoot "src\main\resources\static\css\statement-send-premium-v2.css"
$sourceJs = Join-Path $PSScriptRoot "src\main\resources\static\js\statement-send-premium-v2.js"

$targetCss = Join-Path $ProjectRoot "src\main\resources\static\css\statement-send-premium-v2.css"
$targetJs = Join-Path $ProjectRoot "src\main\resources\static\js\statement-send-premium-v2.js"

$templateRoot = Join-Path $ProjectRoot "src\main\resources\templates"

if (-not (Test-Path $templateRoot)) {
    throw "Template directory not found."
}

New-Item -ItemType Directory -Path (Split-Path -Parent $targetCss) -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path -Parent $targetJs) -Force | Out-Null

Copy-Item $sourceCss $targetCss -Force
Copy-Item $sourceJs $targetJs -Force

Write-Host "[OK] CSS copied" -ForegroundColor Green
Write-Host "[OK] JS copied" -ForegroundColor Green

$cssLine = '    <link rel="stylesheet" th:href="@{/css/statement-send-premium-v2.css(v=20260817_2)}">'
$jsLine = '    <script defer th:src="@{/js/statement-send-premium-v2.js(v=20260817_2)}"></script>'

$utf8 = New-Object System.Text.UTF8Encoding($false)
$changed = 0

$htmlFiles = Get-ChildItem -Path $templateRoot -Recurse -Filter *.html

foreach ($file in $htmlFiles) {
    if ($file.Name -eq "login.html") {
        continue
    }

    $content = [System.IO.File]::ReadAllText($file.FullName)
    $original = $content

    if ($content -notmatch 'statement-send-premium-v2\.css') {
        if ($content -match '</head>') {
            $content = $content -replace '</head>', ($cssLine + "`r`n</head>")
        }
    }

    if ($content -notmatch 'statement-send-premium-v2\.js') {
        if ($content -match '</body>') {
            $content = $content -replace '</body>', ($jsLine + "`r`n</body>")
        }
    }

    if ($content -ne $original) {
        [System.IO.File]::WriteAllText(
            $file.FullName,
            $content,
            $utf8
        )

        $changed++
    }
}

Write-Host "[OK] Templates updated: $changed" -ForegroundColor Green
Write-Host ""
Write-Host "BUILD:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
