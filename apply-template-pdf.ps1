$ErrorActionPreference = 'Stop'

$projectRoot = (Get-Location).Path
$controllerPath = Join-Path $projectRoot 'src\main\java\com\example\salesmgmt\controller\StatementController.java'
$htmlPath = Join-Path $projectRoot 'src\main\resources\templates\statements.html'
$cssPath = Join-Path $projectRoot 'src\main\resources\static\css\statements.css'
$sourceServicePath = Join-Path $PSScriptRoot 'src\main\java\com\example\salesmgmt\service\StatementPdfService.java'
$targetServicePath = Join-Path $projectRoot 'src\main\java\com\example\salesmgmt\service\StatementPdfService.java'
$templatePath = Join-Path $projectRoot 'src\main\resources\template.xlsx'

foreach ($path in @($controllerPath, $htmlPath, $cssPath, $sourceServicePath, $templatePath)) {
    if (-not (Test-Path $path)) {
        throw "필수 파일을 찾을 수 없습니다: $path"
    }
}

$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$backupDir = Join-Path $projectRoot ".patch-backup\statement-template-pdf-$stamp"
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
Copy-Item $controllerPath (Join-Path $backupDir 'StatementController.java')
Copy-Item $htmlPath (Join-Path $backupDir 'statements.html')
Copy-Item $cssPath (Join-Path $backupDir 'statements.css')
if (Test-Path $targetServicePath) {
    Copy-Item $targetServicePath (Join-Path $backupDir 'StatementPdfService.java')
}

function Read-Utf8([string]$path) {
    return [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
}

function Write-Utf8([string]$path, [string]$text) {
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, $text, $utf8NoBom)
}

# 1) 새 PDF 서비스 복사
New-Item -ItemType Directory -Force -Path (Split-Path $targetServicePath) | Out-Null
Copy-Item $sourceServicePath $targetServicePath -Force

# 2) Controller에 StatementPdfService 주입 + /download-pdf 엔드포인트 추가
$controller = Read-Utf8 $controllerPath

if (-not $controller.Contains('import com.example.salesmgmt.service.StatementPdfService;')) {
    $anchor = 'import com.example.salesmgmt.service.StatementWorkbookService;'
    if (-not $controller.Contains($anchor)) {
        throw 'StatementController.java에서 StatementWorkbookService import를 찾지 못했습니다.'
    }
    $controller = $controller.Replace(
        $anchor,
        $anchor + "`r`n" + 'import com.example.salesmgmt.service.StatementPdfService;'
    )
}

if (-not $controller.Contains('private final StatementPdfService statementPdfService;')) {
    $anchor = '    private final SalesManagementService salesManagementService;'
    if (-not $controller.Contains($anchor)) {
        throw 'StatementController.java에서 필드 삽입 위치를 찾지 못했습니다.'
    }
    $controller = $controller.Replace(
        $anchor,
        $anchor + "`r`n" + '    private final StatementPdfService statementPdfService;'
    )
}

if (-not $controller.Contains('StatementPdfService statementPdfService')) {
    $oldParams = @'
            StatementWorkbookService statementWorkbookService,
            SalesManagementService salesManagementService
'@
    $newParams = @'
            StatementWorkbookService statementWorkbookService,
            SalesManagementService salesManagementService,
            StatementPdfService statementPdfService
'@
    if (-not $controller.Contains($oldParams.TrimStart("`r","`n"))) {
        $pattern = 'StatementWorkbookService statementWorkbookService,\s*SalesManagementService salesManagementService'
        $replacement = 'StatementWorkbookService statementWorkbookService,' + "`r`n" + '            SalesManagementService salesManagementService,' + "`r`n" + '            StatementPdfService statementPdfService'
        $updated = [System.Text.RegularExpressions.Regex]::Replace($controller, $pattern, $replacement, 1)
        if ($updated -eq $controller) {
            throw 'StatementController.java에서 생성자 파라미터를 찾지 못했습니다.'
        }
        $controller = $updated
    } else {
        $controller = $controller.Replace(
            $oldParams.TrimStart("`r","`n"),
            $newParams.TrimStart("`r","`n")
        )
    }
}

if (-not $controller.Contains('this.statementPdfService = statementPdfService;')) {
    $anchor = '        this.salesManagementService = salesManagementService;'
    if (-not $controller.Contains($anchor)) {
        throw 'StatementController.java에서 생성자 할당 위치를 찾지 못했습니다.'
    }
    $controller = $controller.Replace(
        $anchor,
        $anchor + "`r`n" + '        this.statementPdfService = statementPdfService;'
    )
}

if (-not $controller.Contains('@PostMapping("/download-pdf")')) {
$pdfMethod = @'
    @PostMapping("/download-pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestParam(value = "templateFile", required = false) MultipartFile templateFile,
            @RequestParam String month,
            @RequestParam(defaultValue = "false") boolean includeEmpty
    ) {
        try {
            YearMonth selectedMonth = YearMonth.parse(month);
            StatementPdfService.PdfResult result =
                    statementPdfService.generate(
                            templateFile,
                            selectedMonth,
                            includeEmpty
                    );

            String encodedFilename = URLEncoder.encode(
                    result.filename(),
                    StandardCharsets.UTF_8
            ).replace("+", "%20");

            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFilename
                    )
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(result.fileBytes());
        } catch (DateTimeParseException exception) {
            return badRequest("생성 월 형식이 올바르지 않습니다.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return badRequest(exception.getMessage());
        }
    }

'@
    $anchor = '    private ResponseEntity<byte[]> badRequest(String message) {'
    if (-not $controller.Contains($anchor)) {
        throw 'StatementController.java에서 PDF 메서드 삽입 위치를 찾지 못했습니다.'
    }
    $controller = $controller.Replace($anchor, $pdfMethod + $anchor)
}

Write-Utf8 $controllerPath $controller

# 3) statements.html에 PDF 다운로드 버튼 추가
$html = Read-Utf8 $htmlPath
if (-not $html.Contains('/statements/download-pdf')) {
    $buttonPattern = '(?s)<button\s+type="submit"[^>]*>\s*[^<]*엑셀[^<]*</button>'
    $buttonReplacement = @'
<div class="statement-download-actions">
                <button type="submit">거래명세서 엑셀 다운로드</button>
                <button type="submit"
                        class="statement-pdf-button"
                        th:formaction="@{/statements/download-pdf}">
                    템플릿 그대로 PDF 다운로드
                </button>
            </div>
'@
    $updated = [System.Text.RegularExpressions.Regex]::Replace(
        $html,
        $buttonPattern,
        $buttonReplacement.Trim(),
        1
    )
    if ($updated -eq $html) {
        throw 'statements.html에서 엑셀 다운로드 버튼을 찾지 못했습니다.'
    }
    $html = $updated
}
Write-Utf8 $htmlPath $html

# 4) PDF 버튼 스타일 추가
$css = Read-Utf8 $cssPath
$marker = '/* template-pdf-export */'
if (-not $css.Contains($marker)) {
$cssAddition = @'

/* template-pdf-export */
.statement-download-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
}

.statement-download-actions > button {
    width: 100%;
}

.statement-pdf-button {
    background: #ffffff;
    color: #16884a;
    border: 1px solid #16884a;
}

.statement-pdf-button:hover {
    background: #edf9f2;
}

@media (max-width: 768px) {
    .statement-download-actions {
        grid-template-columns: 1fr;
    }
}
'@
    $css = $css + $cssAddition
}
Write-Utf8 $cssPath $css

Write-Host ''
Write-Host '완료: template.xlsx 양식을 사용한 PDF 다운로드 기능을 추가했습니다.' -ForegroundColor Green
Write-Host "백업 위치: $backupDir"
Write-Host ''
Write-Host '다음 명령으로 컴파일 확인:' -ForegroundColor Cyan
Write-Host '.\gradlew.bat clean compileJava'
Write-Host ''
Write-Host '로컬에서 PDF 버튼까지 테스트하려면 LibreOffice가 설치되어 있어야 합니다.' -ForegroundColor Yellow
Write-Host 'Railway 앱 서비스 Variables에 아래 값을 추가한 뒤 재배포하세요:' -ForegroundColor Yellow
Write-Host 'RAILPACK_DEPLOY_APT_PACKAGES=... libreoffice-calc fonts-noto-cjk'
