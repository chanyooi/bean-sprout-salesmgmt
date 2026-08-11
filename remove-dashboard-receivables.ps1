$ErrorActionPreference = 'Stop'

$path = Join-Path (Get-Location) 'src/main/resources/templates/dashboard.html'
if (-not (Test-Path $path)) {
    throw "dashboard.html not found: $path"
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$text = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

# Remove only the dashboard panel that renders receivables.vendorRows.
$pattern = '(?s)\s*<article class="panel">(?:(?!<article class="panel">).)*?receivables\.vendorRows(?:(?!<article class="panel">).)*?</article>'
$matches = [System.Text.RegularExpressions.Regex]::Matches($text, $pattern)

if ($matches.Count -ne 1) {
    throw "Expected exactly 1 receivables panel, found $($matches.Count). No changes made."
}

$backup = "$path.before-receivables-removal.bak"
Copy-Item $path $backup -Force

$updated = [System.Text.RegularExpressions.Regex]::Replace($text, $pattern, '', 1)
[System.IO.File]::WriteAllText($path, $updated, $utf8NoBom)

Write-Host 'Dashboard receivables vendor list removed.'
Write-Host "Backup: $backup"
Write-Host 'The receivables summary metric/link remains; detailed vendor rows stay on /payments.'
