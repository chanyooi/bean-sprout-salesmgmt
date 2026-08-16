Statement column order patch

Fixed display order:
1. Doojeol
2. General bean sprout
3. Curly bean sprout
4. 3.5kg General
5. 3.5kg Curly
6. Mung bean sprout

Only products that are already present in the table remain visible.
Special/extra columns remain after the six standard products and before Daily Total.

Apply:
powershell -ExecutionPolicy Bypass -File .\songcheon-statement-column-order\apply-statement-column-order.ps1

Build:
.\gradlew.bat clean build -x test

The PowerShell script is ASCII-only for Windows PowerShell 5.1 compatibility.
