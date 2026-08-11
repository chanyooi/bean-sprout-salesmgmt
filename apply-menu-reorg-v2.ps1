#requires -Version 5.1
$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Read-Utf8([string]$Path) {
    if (-not (Test-Path $Path)) { throw "파일을 찾을 수 없습니다: $Path" }
    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

function Write-Utf8([string]$Path, [string]$Text) {
    [System.IO.File]::WriteAllText($Path, $Text, $utf8NoBom)
}

function Backup-File([string]$Path) {
    $stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
    Copy-Item $Path "$Path.menu-reorg-$stamp.bak" -Force
}

function Replace-Literal([string]$Text, [string]$Old, [string]$New, [string]$Label) {
    if (-not $Text.Contains($Old)) {
        Write-Host "[건너뜀] $Label - 이미 변경됐거나 현재 파일 구조가 다릅니다." -ForegroundColor Yellow
        return $Text
    }
    Write-Host "[변경] $Label" -ForegroundColor Green
    return $Text.Replace($Old, $New)
}

function Replace-RegexLiteral([string]$Text, [string]$Pattern, [string]$New, [string]$Label) {
    $rx = New-Object System.Text.RegularExpressions.Regex($Pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)
    if (-not $rx.IsMatch($Text)) {
        Write-Host "[건너뜀] $Label - 이미 변경됐거나 현재 파일 구조가 다릅니다." -ForegroundColor Yellow
        return $Text
    }
    Write-Host "[변경] $Label" -ForegroundColor Green
    return $rx.Replace($Text, [System.Text.RegularExpressions.MatchEvaluator]{ param($m) $New }, 1)
}

$root = (Get-Location).Path
$tpl = Join-Path $root 'src\main\resources\templates'

$dashboard = Join-Path $tpl 'dashboard.html'
$upload = Join-Path $tpl 'upload.html'
$statements = Join-Path $tpl 'statements.html'
$statementExport = Join-Path $tpl 'statement-export.html'
$statementSend = Join-Path $tpl 'statement-send.html'
$prices = Join-Path $tpl 'prices.html'
$profit = Join-Path $tpl 'profit.html'
$inventory = Join-Path $tpl 'inventory.html'

$targets = @($dashboard,$upload,$statements,$statementExport,$statementSend,$prices,$profit,$inventory)
foreach ($f in $targets) { Backup-File $f }

# 1) Dashboard: 중복 메뉴를 업무 단위로 축소
$t = Read-Utf8 $dashboard
$dashboardMenu = @'
            <div class="quick-menu-grid">
                <a th:if="${isAdmin}" th:href="@{/upload}">장부 관리</a>
                <a th:href="@{/statements}">거래명세서</a>
                <a th:href="@{/sales(month=${selectedMonth})}">판매내역</a>
                <a th:href="@{/sales-calendar(month=${selectedMonth})}">일별 매출 캘린더</a>
                <a th:if="${isAdmin}" th:href="@{/prices}">거래처별 단가 관리</a>
                <a th:if="${isAdmin}" th:href="@{/promotions}">행사·임시단가</a>
                <a th:href="@{/profit(month=${selectedMonth})}">원가·이익 · 콩 재고</a>
                <a th:href="@{/payments(month=${selectedMonth})}">입금·미수금</a>
                <a th:href="@{/vendors}">거래처·배송코스</a>
                <a th:if="${isAdmin}" th:href="@{/admin/safety}">업로드 이력·월마감</a>
                <a th:if="${isAdmin}" th:href="@{/admin/users}">사용자 계정관리</a>
            </div>
'@
$t = Replace-RegexLiteral $t '<div class="quick-menu-grid">.*?</div>' $dashboardMenu '대시보드 빠른 메뉴 정리'
Write-Utf8 $dashboard $t

# 2) Upload: 새 월 장부를 장부 관리 내부 기능으로 배치
$t = Read-Utf8 $upload
$t = Replace-Literal $t '<h1>input_data.xlsx 업로드</h1>' '<h1>장부 업로드</h1>' '장부 업로드 제목'
$t = Replace-Literal $t '                <a class="secondary-link" th:href="@{/input-template}">새 월 장부 다운로드</a>' '' '상단 새 월 장부 버튼 제거'
$t = Replace-Literal $t '                <a class="secondary-link" th:href="@{/inventory}">콩 재고</a>' '' '장부 화면 콩 재고 바로가기 제거'
$t = Replace-Literal $t '>명세서 생성</a>' '>거래명세서</a>' '장부 화면 명세서 이름 변경'
$t = Replace-Literal $t '>단가 관리</a>' '>거래처별 단가 관리</a>' '장부 화면 단가 이름 변경'
$uploadInsert = @'
    <section class="panel">
        <div class="panel-heading">
            <div>
                <h2>새 월 장부</h2>
                <p>새 달 입력을 시작할 때 사용할 장부 파일을 여기에서 받습니다.</p>
            </div>
            <a class="primary-link" th:href="@{/input-template}">새 월 장부 다운로드</a>
        </div>
    </section>
'@
$t = Replace-Literal $t '    <section class="panel">' ($uploadInsert + '    <section class="panel">') '장부 업로드 안에 새 월 장부 추가'
Write-Utf8 $upload $t

# 3) 거래명세서 메인: 엑셀/PDF·이미지·공유/발송 기록을 한 메뉴 안에서 연결
$t = Read-Utf8 $statements
$t = Replace-Literal $t '<title>월간 명세서 생성</title>' '<title>거래명세서</title>' '거래명세서 브라우저 제목'
$t = Replace-Literal $t '<h1>월간 명세서 자동 생성</h1>' '<h1>거래명세서 생성</h1>' '거래명세서 제목'
$statementButtons = @'
            <div class="button-row">
                <a class="secondary-link home-dashboard-link" th:href="@{/}">홈(대시보드)</a>
                <a class="secondary-link" th:href="@{/statement-export(month=${selectedMonth})}">PDF·이미지·공유</a>
                <a class="secondary-link" th:href="@{/statement-send(month=${selectedMonth})}">발송 기록</a>
            </div>
'@
$t = Replace-RegexLiteral $t '<div class="button-row">.*?</div>' $statementButtons '거래명세서 상단 메뉴 통합'
$t = Replace-Literal $t '<h2>명세서 파일 만들기</h2>' '<h2>엑셀 거래명세서 만들기</h2>' '엑셀 거래명세서 구분'
$t = Replace-Literal $t '>명세서 엑셀 다운로드</button>' '>거래명세서 엑셀 다운로드</button>' '엑셀 다운로드 버튼 이름'
Write-Utf8 $statements $t

# 4) PDF/이미지 화면: 거래명세서 하위 화면으로 명칭 통일
$t = Read-Utf8 $statementExport
$t = Replace-Literal $t '<title>명세서 PDF·이미지</title>' '<title>거래명세서 PDF·이미지·공유</title>' 'PDF 화면 브라우저 제목'
$t = Replace-Literal $t '<p class="eyebrow">명세서 공유</p>' '<p class="eyebrow">거래명세서</p>' 'PDF 화면 상위 메뉴 표시'
$t = Replace-Literal $t '<h1>명세서 PDF · 이미지 다운로드</h1>' '<h1>거래명세서 PDF · 이미지 · 공유</h1>' 'PDF 화면 제목'
$exportButtons = @'
            <div class="button-row">
                <a class="secondary-link home-dashboard-link" th:href="@{/}">홈(대시보드)</a>
                <a class="secondary-link" th:href="@{/statements}">엑셀 거래명세서</a>
                <a class="secondary-link" th:href="@{/statement-send(month=${selectedMonth})}">발송 기록</a>
            </div>
'@
$t = Replace-RegexLiteral $t '<div class="button-row">.*?</div>' $exportButtons 'PDF 화면 거래명세서 하위 메뉴'
Write-Utf8 $statementExport $t

# 5) 발송 기록 화면: 명세서 탭의 하위 기능처럼 정리
$t = Read-Utf8 $statementSend
$t = Replace-Literal $t '<title>문자 명세서 발송관리</title>' '<title>거래명세서 발송 기록</title>' '발송 기록 브라우저 제목'
$t = Replace-Literal $t '<p class="eyebrow">월 명세서</p>' '<p class="eyebrow">거래명세서</p>' '발송 기록 상위 메뉴 표시'
$t = Replace-Literal $t '<h1>문자 명세서 발송관리</h1>' '<h1>거래명세서 발송 기록</h1>' '발송 기록 제목'
$sendButtons = @'
            <div class="button-row">
                <a class="secondary-link home-dashboard-link" th:href="@{/}">홈(대시보드)</a>
                <a class="secondary-link" th:href="@{/statements}">엑셀 거래명세서</a>
                <a class="secondary-link" th:href="@{/statement-export(month=${selectedMonth})}">PDF·이미지·공유</a>
            </div>
'@
$t = Replace-RegexLiteral $t '<div class="button-row">.*?</div>' $sendButtons '발송 기록 거래명세서 하위 메뉴'
$t = Replace-Literal $t '문자 명세서 발송관리' '거래명세서 발송 기록' '발송 기록 문구 통일'
$t = Replace-Literal $t '문자 발송 목록' '명세서 발송 목록' '발송 목록 문구 통일'
Write-Utf8 $statementSend $t

# 6) 단가 화면 명칭 통일
$t = Read-Utf8 $prices
$t = Replace-Literal $t '<h1>거래처별 품목 단가</h1>' '<h1>거래처별 단가 관리</h1>' '거래처별 단가 제목'
$t = Replace-Literal $t '                <a class="secondary-link" th:href="@{/inventory}">콩 재고</a>' '' '단가 화면 콩 재고 바로가기 제거'
$t = Replace-Literal $t '>명세서 생성</a>' '>거래명세서</a>' '단가 화면 명세서 이름 변경'
Write-Utf8 $prices $t

# 7) 원가·이익: 콩 재고를 같은 관리 영역의 탭처럼 연결
$t = Read-Utf8 $profit
$t = Replace-Literal $t '<title>원가·예상이익</title>' '<title>원가·이익</title>' '원가·이익 브라우저 제목'
$t = Replace-Literal $t '<h1>월 원가·예상이익</h1>' '<h1>원가·이익</h1>' '원가·이익 제목'
$t = Replace-Literal $t '>콩 재고</a>' '>콩 재고 관리</a>' '원가 화면 콩 재고 이름'
$profitTabs = @'
    <section class="panel">
        <div class="button-row">
            <a class="primary-link" th:href="@{/profit(month=${selectedMonth})}">수익 분석</a>
            <a class="secondary-link" th:href="@{/inventory}">콩 재고 관리</a>
        </div>
    </section>
'@
$t = Replace-Literal $t '    <div class="alert success"' ($profitTabs + '    <div class="alert success"') '원가·이익 내부 수익/재고 탭'
Write-Utf8 $profit $t

# 8) 재고 화면도 원가·이익의 하위 화면처럼 표시
$t = Read-Utf8 $inventory
$t = Replace-Literal $t '<title>콩 재고 관리</title>' '<title>원가·이익 - 콩 재고 관리</title>' '재고 브라우저 제목'
$t = Replace-Literal $t '<p class="eyebrow">원재료 관리</p>' '<p class="eyebrow">원가·이익 관리</p>' '재고 상위 메뉴 표시'
$t = Replace-Literal $t '>명세서 생성</a>' '>거래명세서</a>' '재고 화면 명세서 이름 변경'
$inventoryTabs = @'
    <section class="panel">
        <div class="button-row">
            <a class="secondary-link" th:href="@{/profit}">수익 분석</a>
            <a class="primary-link" th:href="@{/inventory}">콩 재고 관리</a>
        </div>
    </section>
'@
$t = Replace-Literal $t '    <div class="alert success"' ($inventoryTabs + '    <div class="alert success"') '재고 화면 원가·이익 내부 탭'
Write-Utf8 $inventory $t

Write-Host ''
Write-Host '메뉴 구조 정리가 완료되었습니다.' -ForegroundColor Cyan
Write-Host '이제 .\\gradlew.bat clean bootRun 으로 실행해서 화면을 확인하세요.' -ForegroundColor Cyan
