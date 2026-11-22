$ErrorActionPreference = 'Stop'
$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runtime = Join-Path $workspace '.runtime/phase0'
$adminFile = Join-Path $runtime 'mysql-admin.cnf'
$admin = Join-Path $runtime 'tools/mysql-8.4.13-winx64/bin/mysqladmin.exe'
if (-not (Test-Path -LiteralPath $adminFile)) { throw 'Local admin option file not found; no server was stopped.' }
$portLine = [System.IO.File]::ReadAllLines($adminFile) | Where-Object { $_ -match '^port=\d+$' } | Select-Object -First 1
if (-not $portLine) { throw 'Port not found in local option file.' }
$port = [int]($portLine -split '=')[1]
$listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop
$processInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
$expectedServer = Join-Path $runtime 'tools/mysql-8.4.13-winx64/bin/mysqld.exe'
$expectedData = Join-Path $runtime 'mysql-data'
if ([System.IO.Path]::GetFullPath($processInfo.ExecutablePath) -ne [System.IO.Path]::GetFullPath($expectedServer) -or -not $processInfo.CommandLine.Contains($expectedData.Replace('/','\'))) {
    throw 'Listener is not the known workspace instance; not stopped.'
}
& $admin "--defaults-extra-file=$adminFile" shutdown
if ($LASTEXITCODE -ne 0) { throw 'Local MySQL shutdown failed; no unrelated process was terminated.' }
$remaining = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
if ($remaining) { Wait-Process -Id $listener.OwningProcess -Timeout 45 -ErrorAction Stop }
Write-Output 'Workspace phase-0 MySQL shut down cleanly.'
