param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 월 가중평균 콩 원가 패치 적용 ===" -ForegroundColor Cyan
Write-Host "프로젝트: $ProjectRoot"

$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$PatchRoot = Join-Path $ScriptRoot "patch"

if (-not (Test-Path $PatchRoot)) {
    Write-Host "오류: patch 폴더를 찾을 수 없습니다." -ForegroundColor Red
    exit 1
}

# Spring Boot 프로젝트 루트인지 간단 확인
$gradle = Join-Path $ProjectRoot "build.gradle"
$pom = Join-Path $ProjectRoot "pom.xml"

if (-not (Test-Path $gradle) -and -not (Test-Path $pom)) {
    Write-Host "오류: 현재 폴더가 프로젝트 루트로 보이지 않습니다." -ForegroundColor Red
    Write-Host "build.gradle 또는 pom.xml이 있는 폴더에서 실행하세요."
    exit 1
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupRoot = Join-Path $ProjectRoot ("backup-before-monthly-cost-" + $timestamp)

$files = Get-ChildItem -Path $PatchRoot -Recurse -File

if ($files.Count -eq 0) {
    Write-Host "오류: patch 폴더에 덮어쓸 파일이 없습니다." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "적용할 파일: $($files.Count)개"
Write-Host ""

foreach ($file in $files) {
    $relative = $file.FullName.Substring($PatchRoot.Length).TrimStart('\','/')
    $target = Join-Path $ProjectRoot $relative
    $targetDir = Split-Path -Parent $target

    # 원본과 대상이 같은 파일인지 방지
    $sourceFull = [System.IO.Path]::GetFullPath($file.FullName)
    $targetFull = [System.IO.Path]::GetFullPath($target)

    if ($sourceFull -eq $targetFull) {
        Write-Host "건너뜀(원본=대상): $relative" -ForegroundColor Yellow
        continue
    }

    # 기존 파일이 있으면 백업
    if (Test-Path $target) {
        $backupTarget = Join-Path $BackupRoot $relative
        $backupDir = Split-Path -Parent $backupTarget

        if (-not (Test-Path $backupDir)) {
            New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
        }

        Copy-Item -Path $target -Destination $backupTarget -Force
        Write-Host "[백업] $relative"
    }

    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }

    Copy-Item -Path $file.FullName -Destination $target -Force
    Write-Host "[적용] $relative" -ForegroundColor Green
}

Write-Host ""
Write-Host "패치 적용 완료." -ForegroundColor Cyan

if (Test-Path $BackupRoot) {
    Write-Host "백업 위치: $BackupRoot"
} else {
    Write-Host "기존 파일이 없어 백업 파일은 생성되지 않았습니다."
}

Write-Host ""
Write-Host "이제 아래 명령으로 빌드 확인하세요:"
if (Test-Path (Join-Path $ProjectRoot "gradlew.bat")) {
    Write-Host ".\gradlew.bat clean build -x test" -ForegroundColor Yellow
} elseif (Test-Path (Join-Path $ProjectRoot "mvnw.cmd")) {
    Write-Host ".\mvnw.cmd clean package -DskipTests" -ForegroundColor Yellow
} else {
    Write-Host "Gradle 또는 Maven 빌드 명령을 실행하세요." -ForegroundColor Yellow
}
