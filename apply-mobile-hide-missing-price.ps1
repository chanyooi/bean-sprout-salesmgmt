$ErrorActionPreference = 'Stop'

$projectRoot = (Get-Location).Path
$dashboardPath = Join-Path $projectRoot 'src\main\resources\templates\dashboard.html'
$cssPath = Join-Path $projectRoot 'src\main\resources\static\css\operations.css'

if (-not (Test-Path $dashboardPath)) {
    throw "dashboard.html not found: $dashboardPath"
}
if (-not (Test-Path $cssPath)) {
    throw "operations.css not found: $cssPath"
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$dashboard = [System.IO.File]::ReadAllText($dashboardPath, [System.Text.Encoding]::UTF8)
$css = [System.IO.File]::ReadAllText($cssPath, [System.Text.Encoding]::UTF8)

# Backup once.
$dashboardBackup = "$dashboardPath.mobile-missing-price.bak"
$cssBackup = "$cssPath.mobile-missing-price.bak"
if (-not (Test-Path $dashboardBackup)) {
    [System.IO.File]::WriteAllText($dashboardBackup, $dashboard, $utf8NoBom)
}
if (-not (Test-Path $cssBackup)) {
    [System.IO.File]::WriteAllText($cssBackup, $css, $utf8NoBom)
}

# Add a mobile-only hide class to the missing-price metric card.
if ($dashboard -notmatch 'mobile-hide-missing-price-card') {
    $pattern = '(?s)<a class="metric-card"\s*\r?\n\s*th:classappend="\$\{sales\.missingPriceCount > 0\} \? '' danger-metric'' : ''''"\s*\r?\n\s*th:href="@\{/sales\(month=\$\{selectedMonth\}, missingPriceOnly=true\)\}">'
    $replacement = '<a class="metric-card mobile-hide-missing-price-card"' + "`r`n" + '           th:classappend="${sales.missingPriceCount > 0} ? '' danger-metric'' : ''''"' + "`r`n" + '           th:href="@{/sales(month=${selectedMonth}, missingPriceOnly=true)}">'
    $updated = [regex]::Replace($dashboard, $pattern, $replacement, 1)

    if ($updated -eq $dashboard) {
        # Fallback: add class based only on the unique href, regardless of formatting.
        $pattern2 = '(?s)<a\s+class="metric-card"(?<attrs>[^>]*?)th:href="@\{/sales\(month=\$\{selectedMonth\},\s*missingPriceOnly=true\)\}"(?<tail>[^>]*)>'
        $replacement2 = '<a class="metric-card mobile-hide-missing-price-card"${attrs}th:href="@{/sales(month=${selectedMonth}, missingPriceOnly=true)}"${tail}>'
        $updated = [regex]::Replace($dashboard, $pattern2, $replacement2, 1)
    }

    if ($updated -eq $dashboard) {
        throw 'Could not find the missing-price metric card. No files were changed.'
    }
    $dashboard = $updated
}

# Add a class to the bottom missing-price warning so mobile does not show it either.
if ($dashboard -notmatch 'mobile-hide-missing-price-alert') {
    $alertPattern = '<div class="alert warning"\s*\r?\n\s*th:if="\$\{sales\.missingPriceCount > 0\}">'
    $alertReplacement = '<div class="alert warning mobile-hide-missing-price-alert"' + "`r`n" + '         th:if="${sales.missingPriceCount > 0}">'
    $dashboard = [regex]::Replace($dashboard, $alertPattern, $alertReplacement, 1)
}

$cssMarker = '/* mobile-hide-missing-price */'
if ($css -notmatch [regex]::Escape($cssMarker)) {
    $css += @'

/* mobile-hide-missing-price */
@media (max-width: 768px) {
    .mobile-hide-missing-price-card,
    .mobile-hide-missing-price-alert {
        display: none !important;
    }
}
'@
}

[System.IO.File]::WriteAllText($dashboardPath, $dashboard, $utf8NoBom)
[System.IO.File]::WriteAllText($cssPath, $css, $utf8NoBom)

Write-Host 'Done: missing-price card and warning are hidden only on mobile (<= 768px).'
Write-Host 'Desktop behavior is unchanged.'
