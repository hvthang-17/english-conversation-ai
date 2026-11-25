$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ([string]::IsNullOrWhiteSpace($env:P0_DB_USERNAME)) { throw 'Set P0_DB_USERNAME.' }
$previous = $env:P0_RUN_MYSQL_TEST
$env:P0_RUN_MYSQL_TEST = 'true'
Push-Location (Join-Path $root 'backend')
try {
    & '.\mvnw.cmd' --batch-mode '-Dtest=PhaseZeroMysqlTest' test
    if ($LASTEXITCODE -ne 0) { throw 'P0 MySQL verification failed.' }
} finally {
    Pop-Location
    $env:P0_RUN_MYSQL_TEST = $previous
}
