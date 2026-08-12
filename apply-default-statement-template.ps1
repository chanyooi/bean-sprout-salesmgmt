$ErrorActionPreference = 'Stop'

$projectRoot = (Get-Location).Path
$controllerPath = Join-Path $projectRoot 'src\main\java\com\example\salesmgmt\controller\StatementController.java'
$servicePath = Join-Path $projectRoot 'src\main\java\com\example\salesmgmt\service\StatementWorkbookService.java'
$htmlPath = Join-Path $projectRoot 'src\main\resources\templates\statements.html'
$templatePath = Join-Path $projectRoot 'src\main\resources\template.xlsx'

$required = @($controllerPath, $servicePath, $htmlPath, $templatePath)
foreach ($path in $required) {
    if (-not (Test-Path $path)) {
        throw "필수 파일을 찾을 수 없습니다: $path"
    }
}

$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$backupDir = Join-Path $projectRoot ".patch-backup\statement-template-$stamp"
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
Copy-Item $controllerPath (Join-Path $backupDir 'StatementController.java')
Copy-Item $servicePath (Join-Path $backupDir 'StatementWorkbookService.java')
Copy-Item $htmlPath (Join-Path $backupDir 'statements.html')

function Read-Utf8([string]$path) {
    return [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
}

function Write-Utf8([string]$path, [string]$text) {
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, $text, $utf8NoBom)
}

# 1) Controller: 템플릿 업로드를 선택사항으로 변경
$controller = Read-Utf8 $controllerPath
$oldControllerParam = '@RequestParam("templateFile") MultipartFile templateFile,'
$newControllerParam = '@RequestParam(value = "templateFile", required = false) MultipartFile templateFile,'
if ($controller.Contains($oldControllerParam)) {
    $controller = $controller.Replace($oldControllerParam, $newControllerParam)
} elseif (-not $controller.Contains($newControllerParam)) {
    throw 'StatementController.java에서 templateFile 파라미터를 찾지 못했습니다.'
}
Write-Utf8 $controllerPath $controller

# 2) Service: 업로드 파일이 없으면 classpath의 src/main/resources/template.xlsx 사용
$service = Read-Utf8 $servicePath

if (-not $service.Contains('import org.springframework.core.io.ClassPathResource;')) {
    $anchor = 'import org.springframework.stereotype.Service;'
    if (-not $service.Contains($anchor)) {
        throw 'StatementWorkbookService.java에서 import 삽입 위치를 찾지 못했습니다.'
    }
    $service = $service.Replace($anchor, "import org.springframework.core.io.ClassPathResource;`r`n$anchor")
}

$constant = '    private static final String DEFAULT_TEMPLATE_PATH = "template.xlsx";'
if (-not $service.Contains($constant)) {
    $anchor = '    private static final String WARNING_SHEET_NAME = "생성확인";'
    if (-not $service.Contains($anchor)) {
        throw 'StatementWorkbookService.java에서 상수 삽입 위치를 찾지 못했습니다.'
    }
    $service = $service.Replace($anchor, "$anchor`r`n$constant")
}

$service = $service.Replace('        validateTemplate(templateFile);' + "`r`n`r`n", '')
$service = $service.Replace('        validateTemplate(templateFile);' + "`n`n", '')
$service = $service.Replace('templateFile.getInputStream()', 'openTemplate(templateFile)')

$openTemplateMethod = @'
    private InputStream openTemplate(MultipartFile templateFile) throws IOException {
        if (templateFile != null && !templateFile.isEmpty()) {
            String originalFilename = templateFile.getOriginalFilename();
            if (originalFilename == null
                    || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                throw new IllegalArgumentException(
                        ".xlsx 형식의 템플릿만 사용할 수 있습니다."
                );
            }
            return templateFile.getInputStream();
        }

        ClassPathResource resource = new ClassPathResource(DEFAULT_TEMPLATE_PATH);
        if (!resource.exists()) {
            throw new IllegalArgumentException(
                    "기본 template.xlsx 파일을 찾을 수 없습니다. "
                            + "src/main/resources/template.xlsx를 확인해주세요."
            );
        }
        return resource.getInputStream();
    }

'@

if (-not $service.Contains('private InputStream openTemplate(MultipartFile templateFile)')) {
    $pattern = '(?s)    private void validateTemplate\(MultipartFile templateFile\) \{.*?\r?\n    \}\r?\n\r?\n(?=    private String normalizeName)'
    $updated = [System.Text.RegularExpressions.Regex]::Replace($service, $pattern, $openTemplateMethod)
    if ($updated -eq $service) {
        throw 'StatementWorkbookService.java에서 기존 validateTemplate 메서드를 찾지 못했습니다.'
    }
    $service = $updated
}
Write-Utf8 $servicePath $service

# 3) HTML: template.xlsx는 선택사항. 선택하지 않으면 내장 기본 템플릿 사용
$html = Read-Utf8 $htmlPath
$html = $html.Replace('<label for="templateFile">template.xlsx</label>', '<label for="templateFile">새 템플릿 사용 (선택)</label>')

$requiredPattern = '(?s)(<input\s+id="templateFile"\s+name="templateFile"\s+type="file"\s+accept="\.xlsx")\s+required>'
$html = [System.Text.RegularExpressions.Regex]::Replace($html, $requiredPattern, '$1>')

if (-not $html.Contains('파일을 선택하지 않으면 서버에 등록된 기본 template.xlsx를 자동으로 사용합니다.')) {
    $fileDivPattern = '(?s)(<div class="statement-field file-field">\s*<label for="templateFile">.*?</label>\s*<input\s+id="templateFile"\s+name="templateFile"\s+type="file"\s+accept="\.xlsx"\s*>)(\s*</div>)'
    $replacement = '$1' + "`r`n" + '                <small>파일을 선택하지 않으면 서버에 등록된 기본 template.xlsx를 자동으로 사용합니다. 거래처 추가 등으로 새 양식이 필요할 때만 새 .xlsx 파일을 선택하세요.</small>' + '$2'
    $updated = [System.Text.RegularExpressions.Regex]::Replace($html, $fileDivPattern, $replacement)
    if ($updated -eq $html) {
        throw 'statements.html에서 templateFile 입력 영역을 찾지 못했습니다.'
    }
    $html = $updated
}
Write-Utf8 $htmlPath $html

Write-Host ''
Write-Host '완료: 기본 template.xlsx 자동 사용 + 필요할 때만 새 템플릿 선택 기능을 적용했습니다.' -ForegroundColor Green
Write-Host "기본 템플릿: $templatePath"
Write-Host "백업 위치: $backupDir"
Write-Host ''
Write-Host '다음 명령으로 확인하세요:' -ForegroundColor Cyan
Write-Host '.\gradlew.bat clean compileJava'
Write-Host '.\gradlew.bat bootRun'
Write-Host ''
Write-Host 'Railway 반영 시 template.xlsx도 반드시 git add 하세요:' -ForegroundColor Yellow
Write-Host 'git add src/main/java/com/example/salesmgmt/controller/StatementController.java'
Write-Host 'git add src/main/java/com/example/salesmgmt/service/StatementWorkbookService.java'
Write-Host 'git add src/main/resources/templates/statements.html'
Write-Host 'git add src/main/resources/template.xlsx'
