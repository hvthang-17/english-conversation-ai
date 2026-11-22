param([switch]$Verify)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-env.ps1')
$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-ProjectEnv -Path (Join-Path $workspace '.env')
Push-Location (Join-Path $workspace 'backend')
try {
    if ($Verify) { & '.\mvnw.cmd' --batch-mode verify }
    else { & '.\mvnw.cmd' --batch-mode spring-boot:run }
    if ($LASTEXITCODE -ne 0) { throw 'Backend command failed.' }
} finally { Pop-Location }
