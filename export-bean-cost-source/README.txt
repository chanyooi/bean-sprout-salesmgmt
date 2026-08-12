사용법

1) 이 폴더를 salesmgmt 프로젝트 안에 넣습니다.
2) PowerShell에서 프로젝트 루트로 이동합니다.
3) 실행:

powershell -ExecutionPolicy Bypass -File .\export-bean-cost-source\export-bean-cost-source.ps1

4) 프로젝트 루트에 bean-cost-latest-source.zip 이 생성됩니다.
5) 그 ZIP을 ChatGPT에 업로드하면 됩니다.

이 스크립트는 소스 파일을 읽어서 ZIP으로 복사할 뿐, 프로젝트 파일을 수정하지 않습니다.
