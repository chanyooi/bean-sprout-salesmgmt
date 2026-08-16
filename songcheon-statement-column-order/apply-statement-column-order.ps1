param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== APPLY STATEMENT COLUMN ORDER ===" -ForegroundColor Cyan

$sourceJs = Join-Path $PSScriptRoot "src\main\resources\static\js\statement-column-order.js"
$targetJs = Join-Path $ProjectRoot "src\main\resources\static\js\statement-column-order.js"
$templateRoot = Join-Path $ProjectRoot "src\main\resources\templates"

if (-not (Test-Path $sourceJs)) {
    throw "Source JS not found."
}

if (-not (Test-Path $templateRoot)) {
    throw "Template directory not found."
}

$targetDir = Split-Path -Parent $targetJs

if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

Copy-Item $sourceJs $targetJs -Force

Write-Host "[OK] JS copied" -ForegroundColor Green

$scriptLine = '    <script defer th:src="@{/js/statement-column-order.js(v=20260817_1)}"></script>'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$changed = 0

$htmlFiles = Get-ChildItem -Path $templateRoot -Recurse -Filter *.html

foreach ($file in $htmlFiles) {
    if ($file.Name -eq "login.html") {
        continue
    }

    $content = [System.IO.File]::ReadAllText($file.FullName)
    $original = $content

    if ($content -notmatch 'statement-column-order\.js') {
        if ($content -match '</body>') {
            $content = $content -replace '</body>', ($scriptLine + "`r`n</body>")
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
