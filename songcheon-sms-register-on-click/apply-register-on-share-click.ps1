param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== 이미지 공유 버튼 클릭 즉시 발송완료 등록 ===" -ForegroundColor Cyan

$template = Join-Path $ProjectRoot "src\main\resources\templates\statement_export.html"

if (-not (Test-Path $template)) {
    throw "statement_export.html을 찾을 수 없습니다: $template"
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backup = Join-Path $ProjectRoot ("statement_export.backup-" + $stamp + ".html")

Copy-Item $template $backup -Force

$content = [System.IO.File]::ReadAllText($template)
$original = $content

# 1. markSent 중복 호출 방지 플래그 추가
if ($content -notmatch 'let sentMarked = false;') {
    $target = '    async function markSent() {'
    $replacement = @'
    let sentMarked = false;

    async function markSent() {
'@
    $content = $content.Replace($target, $replacement)
}

# 2. markSent 안에서 이미 저장된 경우 재호출 방지
$oldGuard = @'
        if (!managedSmsRecipient || !vendorId) {
            return;
        }
'@

$newGuard = @'
        if (!managedSmsRecipient || !vendorId || sentMarked) {
            return;
        }
'@

if ($content.Contains($oldGuard)) {
    $content = $content.Replace($oldGuard, $newGuard)
}

# 3. fetch 성공 후 플래그 설정
$oldFetch = @'
        await fetch('/statement-send/mark-sent', {
            method: 'POST',
            headers,
            body
        });
'@

$newFetch = @'
        const response = await fetch('/statement-send/mark-sent', {
            method: 'POST',
            headers,
            body
        });

        if (!response.ok) {
            throw new Error('발송완료 저장 실패');
        }

        sentMarked = true;
'@

if ($content.Contains($oldFetch)) {
    $content = $content.Replace($oldFetch, $newFetch)
}

# 4. 공유 버튼 클릭 직후 markSent 실행
$oldClick = @'
    shareButton.addEventListener('click', async () => {
        shareButton.disabled = true;
        shareButton.textContent = '이미지 만드는 중...';

        try {
'@

$newClick = @'
    shareButton.addEventListener('click', async () => {
        shareButton.disabled = true;
        shareButton.textContent = '발송완료 등록 중...';

        try {
            try {
                await markSent();
            } catch (markError) {
                console.warn('[mark-sent-on-click]', markError);
            }

            shareButton.textContent = '이미지 만드는 중...';
'@

if ($content.Contains($oldClick)) {
    $content = $content.Replace($oldClick, $newClick)
}
else {
    throw "공유 버튼 클릭 코드를 찾지 못했습니다. 현재 statement_export.html 구조가 예상과 다릅니다."
}

# 5. 안내문도 현재 동작에 맞게 변경
$content = $content.Replace(
    '공유 성공 시 이번 달 발송완료로 자동 기록합니다.',
    '‘이미지로 바로 공유’를 누르는 순간 이번 달 발송완료로 자동 기록합니다.'
)

# 6. 공유 후 버튼 문구를 클릭기준으로 변경
$content = $content.Replace(
    "'공유 완료 · 발송기록 저장됨'",
    "'발송완료 등록됨 · 공유 완료'"
)

if ($content -eq $original) {
    throw "수정된 내용이 없습니다."
}

$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($template, $content, $utf8)

Write-Host "[완료] statement_export.html 수정" -ForegroundColor Green
Write-Host "백업: $backup"
Write-Host ""
Write-Host "동작:" -ForegroundColor Cyan
Write-Host "1. 이미지로 바로 공유 버튼 클릭"
Write-Host "2. 즉시 발송완료 DB 저장"
Write-Host "3. 문자발송완료 표 자동 갱신"
Write-Host "4. 그 다음 이미지 생성/공유창 실행"
Write-Host ""
Write-Host "빌드:" -ForegroundColor Cyan
Write-Host ".\gradlew.bat clean build -x test"
