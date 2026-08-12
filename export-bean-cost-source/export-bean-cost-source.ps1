param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 콩 원가 관련 최신 소스 묶기 ===" -ForegroundColor Cyan
Write-Host "프로젝트: $ProjectRoot"
Write-Host ""

$required = @(
    "src\main\java\com\example\salesmgmt\controller\InventoryController.java",
    "src\main\java\com\example\salesmgmt\service\BeanInventoryService.java",
    "src\main\java\com\example\salesmgmt\entity\BeanPurchaseEntity.java",
    "src\main\java\com\example\salesmgmt\entity\BeanUsageEntity.java",
    "src\main\java\com\example\salesmgmt\repository\BeanPurchaseRepository.java",
    "src\main\java\com\example\salesmgmt\repository\BeanUsageRepository.java"
)

$optional = @(
    "src\main\java\com\example\salesmgmt\entity\BeanStockSettingEntity.java",
    "src\main\java\com\example\salesmgmt\repository\BeanStockSettingRepository.java",
    "src\main\java\com\example\salesmgmt\service\MonthlyProfitService.java",
    "src\main\java\com\example\salesmgmt\controller\ProfitController.java"
)

$temp = Join-Path $ProjectRoot "_bean_cost_export_temp"
$zip = Join-Path $ProjectRoot "bean-cost-latest-source.zip"

if (Test-Path $temp) {
    Remove-Item $temp -Recurse -Force
}
New-Item -ItemType Directory -Path $temp -Force | Out-Null

foreach ($rel in $required) {
    $src = Join-Path $ProjectRoot $rel
    if (-not (Test-Path $src)) {
        Write-Host "필수 파일 없음: $rel" -ForegroundColor Red
        exit 1
    }

    $dest = Join-Path $temp $rel
    $dir = Split-Path -Parent $dest
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    Copy-Item $src $dest -Force
    Write-Host "[포함] $rel"
}

foreach ($rel in $optional) {
    $src = Join-Path $ProjectRoot $rel
    if (Test-Path $src) {
        $dest = Join-Path $temp $rel
        $dir = Split-Path -Parent $dest
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        Copy-Item $src $dest -Force
        Write-Host "[포함] $rel"
    }
}

# inventory/profit 관련 HTML도 자동 포함
$templateRoot = Join-Path $ProjectRoot "src\main\resources\templates"
if (Test-Path $templateRoot) {
    Get-ChildItem $templateRoot -File | Where-Object {
        $_.Name -match "inventory|profit|expense"
    } | ForEach-Object {
        $rel = $_.FullName.Substring($ProjectRoot.Length).TrimStart('\','/')
        $dest = Join-Path $temp $rel
        $dir = Split-Path -Parent $dest
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        Copy-Item $_.FullName $dest -Force
        Write-Host "[포함] $rel"
    }
}

if (Test-Path $zip) {
    Remove-Item $zip -Force
}

Compress-Archive -Path (Join-Path $temp "*") -DestinationPath $zip -Force
Remove-Item $temp -Recurse -Force

Write-Host ""
Write-Host "완료:" -ForegroundColor Green
Write-Host $zip -ForegroundColor Yellow
Write-Host ""
Write-Host "이 ZIP 파일을 ChatGPT 대화에 올려주세요."
