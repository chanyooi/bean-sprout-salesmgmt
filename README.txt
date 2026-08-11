Mobile-only hide patch for the dashboard missing-price UI.

Run from the project root:

powershell -ExecutionPolicy Bypass -File .\apply-mobile-hide-missing-price.ps1
.\gradlew.bat clean bootRun

What it does:
- Desktop: keeps the missing-price metric card and warning.
- Mobile (<= 768px): hides the missing-price metric card and the bottom missing-price warning.
- Does not change Java, DB, or business logic.
- Creates .bak backups of dashboard.html and operations.css before first modification.
