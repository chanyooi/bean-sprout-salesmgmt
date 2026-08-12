$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$project = Get-Location

$files = @(
    "src\main\java\com\example\salesmgmt\controller\InventoryController.java",
    "src\main\resources\templates\inventory.html",
    "src\main\resources\static\css\inventory.css"
)

foreach ($relative in $files) {
    $source = Join-Path $root $relative
    $target = Join-Path $project $relative

    if (-not (Test-Path $source)) {
        throw "패치 파일을 찾을 수 없습니다: $source"
    }

    $targetDir = Split-Path -Parent $target
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

    if (Test-Path $target) {
        Copy-Item $target "$target.bak" -Force
    }

    Copy-Item $source $target -Force
    Write-Host "적용: $relative"
}

Write-Host ""
Write-Host "콩 사용량·원가 화면 패치가 적용되었습니다."
Write-Host "다음 명령으로 확인하세요: .\gradlew.bat clean compileJava"
