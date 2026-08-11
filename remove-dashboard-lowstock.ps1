$ErrorActionPreference = 'Stop'

$path = Join-Path (Get-Location) 'src/main/resources/templates/dashboard.html'
if (-not (Test-Path $path)) {
    throw "dashboard.html not found: $path"
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$text = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$original = $text

# 1) Remove the top dashboard metric card that uses inventory.lowStockCount.
$metricPattern = '(?s)\s*<a class="metric-card"(?:(?!<a class="metric-card").)*?inventory\.lowStockCount(?:(?!<a class="metric-card").)*?</a>'
$metricMatches = [System.Text.RegularExpressions.Regex]::Matches($text, $metricPattern)
if ($metricMatches.Count -gt 1) {
    throw "Expected at most 1 low-stock metric card, found $($metricMatches.Count). No changes made."
}
if ($metricMatches.Count -eq 1) {
    $text = [System.Text.RegularExpressions.Regex]::Replace($text, $metricPattern, '', 1)
    Write-Host 'Removed top low-stock metric card.'
} else {
    Write-Host 'Top low-stock metric card already absent; skipped.'
}

# 2) Remove the detailed dashboard panel that renders lowStockRows.
$panelPattern = '(?s)\s*<article class="panel">(?:(?!<article class="panel">).)*?lowStockRows(?:(?!<article class="panel">).)*?</article>'
$panelMatches = [System.Text.RegularExpressions.Regex]::Matches($text, $panelPattern)
if ($panelMatches.Count -gt 1) {
    throw "Expected at most 1 low-stock panel, found $($panelMatches.Count). No changes made."
}
if ($panelMatches.Count -eq 1) {
    $text = [System.Text.RegularExpressions.Regex]::Replace($text, $panelPattern, '', 1)
    Write-Host 'Removed detailed low-stock panel.'
} else {
    Write-Host 'Detailed low-stock panel already absent; skipped.'
}

# 3) Remove dashboard-grid sections that became completely empty after the panel removal.
$emptyGridPattern = '(?s)\s*<section class="dashboard-grid">\s*</section>'
$text = [System.Text.RegularExpressions.Regex]::Replace($text, $emptyGridPattern, '')

if ($text -eq $original) {
    Write-Host 'No changes were necessary.'
    exit 0
}

$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$backup = "$path.before-lowstock-removal-$stamp.bak"
Copy-Item $path $backup -Force
[System.IO.File]::WriteAllText($path, $text, $utf8NoBom)

Write-Host 'Dashboard low-stock UI removed successfully.'
Write-Host "Backup: $backup"
Write-Host 'Inventory data and /inventory page were not changed.'
