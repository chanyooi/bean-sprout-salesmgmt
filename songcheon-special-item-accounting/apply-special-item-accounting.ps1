param([string]$ProjectRoot = (Get-Location).Path)
$ErrorActionPreference = "Stop"
Write-Host "=== 손두부/두부판/회수통 회계 분리 적용 ===" -ForegroundColor Cyan
$src = Join-Path $PSScriptRoot "src"
$dst = Join-Path $ProjectRoot "src"
Copy-Item (Join-Path $src "main\java\com\example\salesmgmt\service\SpecialItemAccountingService.java") (Join-Path $dst "main\java\com\example\salesmgmt\service\SpecialItemAccountingService.java") -Force
Copy-Item (Join-Path $src "main\java\com\example\salesmgmt\controller\SpecialItemAccountingController.java") (Join-Path $dst "main\java\com\example\salesmgmt\controller\SpecialItemAccountingController.java") -Force
New-Item -ItemType Directory -Path (Join-Path $dst "main\resources\static\css") -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $dst "main\resources\static\js") -Force | Out-Null
Copy-Item (Join-Path $src "main\resources\static\css\special-item-accounting.css") (Join-Path $dst "main\resources\static\css\special-item-accounting.css") -Force
Copy-Item (Join-Path $src "main\resources\static\js\special-item-accounting.js") (Join-Path $dst "main\resources\static\js\special-item-accounting.js") -Force
$templateRoot = Join-Path $dst "main\resources\templates"
$cssLine = '    <link rel="stylesheet" th:href="@{/css/special-item-accounting.css(v=20260814_1)}">'
$jsLine = '    <script defer th:src="@{/js/special-item-accounting.js(v=20260814_1)}"></script>'
$utf8 = New-Object System.Text.UTF8Encoding($false)
Get-ChildItem -Path $templateRoot -Recurse -Filter *.html | ForEach-Object {
  if ($_.Name -eq 'login.html') { return }
  $c=[System.IO.File]::ReadAllText($_.FullName)
  $o=$c
  if ($c -notmatch 'special-item-accounting\.css') { $c=$c -replace '</head>',($cssLine+"`r`n</head>") }
  if ($c -notmatch 'special-item-accounting\.js') { $c=$c -replace '</body>',($jsLine+"`r`n</body>") }
  if ($c -ne $o) { [System.IO.File]::WriteAllText($_.FullName,$c,$utf8) }
}
Write-Host "적용 완료" -ForegroundColor Green
Write-Host ".\gradlew.bat clean build -x test" -ForegroundColor Cyan
