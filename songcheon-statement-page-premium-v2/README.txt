Statement page premium V2

This patch intentionally keeps the PowerShell script ASCII-only
to avoid Windows PowerShell 5.1 Korean/regex encoding errors.

Apply:
powershell -ExecutionPolicy Bypass -File .\songcheon-statement-page-premium-v2\apply-statement-page-premium-v2.ps1

Build:
.\gradlew.bat clean build -x test

The JavaScript activates only on the statement-send page when it finds:
- share button
- view button
- month input
- vendor select
