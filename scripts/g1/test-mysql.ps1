$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ($env:G1_TEST_DB_URL -notmatch '^jdbc:mysql://127\.0\.0\.1:3306/g1_test_[0-9a-f]{12}\?connectionTimeZone=UTC$') {
    throw 'Set G1_TEST_DB_URL to a newly created, empty g1_test_<12 hex characters> schema on local MySQL.'
}

$previous = $env:G1_MYSQL_TEST
$env:G1_MYSQL_TEST = 'true'
Push-Location (Join-Path $root 'backend')
try {
    & '.\mvnw.cmd' --batch-mode verify
    if ($LASTEXITCODE -ne 0) { throw 'G1 MySQL verification failed.' }
} finally {
    Pop-Location
    $env:G1_MYSQL_TEST = $previous
}
