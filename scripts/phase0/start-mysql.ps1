param(
    [string]$ToolDirectory,
    [ValidateRange(1024, 65535)][int]$Port = 3307
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'local-env.ps1')
$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runtime = Join-Path $workspace '.runtime/phase0'
if (-not $ToolDirectory) { $ToolDirectory = Join-Path $runtime 'tools/mysql-8.4.13-winx64' }
$server = Join-Path $ToolDirectory 'bin/mysqld.exe'
$client = Join-Path $ToolDirectory 'bin/mysql.exe'
if (-not (Test-Path -LiteralPath $server)) { throw 'MySQL 8.4 portable not found. See docs/development/phase0/mysql.md.' }
$version = & $server --no-defaults --version
if ($version -notmatch 'Ver 8\.4\.') { throw 'This script requires MySQL 8.4, not another installed server.' }
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$envPath = Join-Path $workspace '.env'
if (-not (Test-Path -LiteralPath $envPath)) {
    $appPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    $rootPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    $lines = @(
        '# Local-only credentials generated for the isolated phase-0 MySQL instance. Never commit.',
        'MYSQL_DATABASE=english_conversation', 'MYSQL_USER=english_app',
        "MYSQL_PASSWORD=$appPassword", "MYSQL_ROOT_PASSWORD=$rootPassword", "MYSQL_PORT=$Port",
        "SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:$Port/english_conversation?connectionTimeZone=UTC",
        'SPRING_DATASOURCE_USERNAME=english_app', "SPRING_DATASOURCE_PASSWORD=$appPassword",
        '# Keep blank until you choose to create a key. Phase 0 currently prohibits live API calls.',
        'GEMINI_API_KEY=', 'GEMINI_CHAT_MODEL=', 'GEMINI_AUDIO_MODEL=', 'GEMINI_FEEDBACK_MODEL='
    )
    [System.IO.File]::WriteAllLines($envPath, $lines, [System.Text.UTF8Encoding]::new($false))
    Write-Output 'Created ignored root .env with random local DB credentials; values are not printed.'
}
$local = Read-ProjectEnv -Path $envPath
foreach ($key in @('MYSQL_DATABASE','MYSQL_USER','MYSQL_PASSWORD','MYSQL_ROOT_PASSWORD','MYSQL_PORT')) {
    if (-not $local.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($local[$key])) { throw "Missing $key in root .env; existing file is preserved." }
}
if ([int]$local['MYSQL_PORT'] -ne $Port) { throw 'Requested port differs from MYSQL_PORT in .env. Inspect configuration instead of overwriting it.' }
$database = $local['MYSQL_DATABASE']
$username = $local['MYSQL_USER']
if ($database -notmatch '^[a-z][a-z0-9_]{0,63}$' -or $username -notmatch '^[a-z][a-z0-9_]{0,31}$') { throw 'Use simple lowercase database/user names for this local bootstrap.' }
foreach ($key in @('MYSQL_PASSWORD','MYSQL_ROOT_PASSWORD')) {
    if ($local[$key] -notmatch '^[A-Za-z0-9_-]{24,128}$') { throw 'Local bootstrap passwords must be 24–128 letters/digits/_/-. No existing value was changed.' }
}
$data = [System.IO.Path]::GetFullPath((Join-Path $runtime 'mysql-data'))
if (-not $data.StartsWith($workspace + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Data directory must stay in the workspace.' }
$adminFile = Join-Path $runtime 'mysql-admin.cnf'
[System.IO.File]::WriteAllLines($adminFile, @('[client]','user=root',"password=$($local['MYSQL_ROOT_PASSWORD'])",'host=127.0.0.1',"port=$Port"), [System.Text.UTF8Encoding]::new($false))
$occupied = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($occupied) {
    & $client "--defaults-extra-file=$adminFile" --batch --skip-column-names --execute='SELECT VERSION();'
    if ($LASTEXITCODE -ne 0) { throw 'Port occupied by an instance that cannot be verified with local credentials; not modified.' }
    Write-Output 'Verified an already-running local instance; did not restart it.'
    exit 0
}
$fresh = -not (Test-Path -LiteralPath (Join-Path $data 'mysql'))
if ($fresh) {
    if ((Test-Path -LiteralPath $data) -and (Get-ChildItem -LiteralPath $data -Force | Select-Object -First 1)) { throw 'Non-empty uninitialized data directory; inspect manually, no cleanup performed.' }
    & $server --no-defaults --initialize-insecure "--basedir=$ToolDirectory" "--datadir=$data" "--log-error=$(Join-Path $runtime 'mysql-init.log')"
    if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed; inspect local log without publishing credentials.' }
}
$initFile = Join-Path $runtime 'mysql-first-start.sql'
$arguments = @('--no-defaults', "--basedir=`"$ToolDirectory`"", "--datadir=`"$data`"", '--bind-address=127.0.0.1', "--port=$Port", '--mysqlx=OFF', '--skip-name-resolve', '--default-time-zone=+00:00', '--character-set-server=utf8mb4', '--collation-server=utf8mb4_0900_ai_ci', "--log-error=`"$(Join-Path $runtime 'mysql-server.log')`"", "--pid-file=`"$(Join-Path $runtime 'mysql-server.pid')`"")
if ($fresh -or (Test-Path -LiteralPath $initFile)) {
    $sql = @(
        "ALTER USER 'root'@'localhost' IDENTIFIED BY '$($local['MYSQL_ROOT_PASSWORD'])';",
        "CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '$($local['MYSQL_ROOT_PASSWORD'])';",
        "GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;",
        "CREATE DATABASE IF NOT EXISTS $database CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;",
        "CREATE USER IF NOT EXISTS '$username'@'127.0.0.1' IDENTIFIED BY '$($local['MYSQL_PASSWORD'])';",
        "GRANT ALL PRIVILEGES ON $database.* TO '$username'@'127.0.0.1';"
    )
    [System.IO.File]::WriteAllLines($initFile, $sql, [System.Text.UTF8Encoding]::new($false))
    $arguments += "--init-file=`"$initFile`""
}
$process = Start-Process -FilePath $server -ArgumentList $arguments -WindowStyle Hidden -PassThru
$deadline = [DateTime]::UtcNow.AddSeconds(50)
$ready = $false
while ([DateTime]::UtcNow -lt $deadline -and -not $process.HasExited) {
    & $client "--defaults-extra-file=$adminFile" --connect-timeout=2 --batch --skip-column-names --execute='SELECT 1;' 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Milliseconds 500
    $process.Refresh()
}
if (-not $ready) { throw 'MySQL not ready; inspect local logs/init file. Do not reinitialize or delete the data directory.' }
if (Test-Path -LiteralPath $initFile) { Remove-Item -LiteralPath $initFile }
Write-Output "MySQL 8.4 ready on 127.0.0.1:$Port; PID $($process.Id). Only the new workspace instance was started."
