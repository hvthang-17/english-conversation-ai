$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-env.ps1')
$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runtime = Join-Path $workspace '.runtime/phase0'
$local = Read-ProjectEnv -Path (Join-Path $workspace '.env')
$client = Join-Path $runtime 'tools/mysql-8.4.13-winx64/bin/mysql.exe'
$options = Join-Path $runtime 'mysql-app-probe.cnf'
try {
    [System.IO.File]::WriteAllLines($options, @('[client]',"user=$($local['MYSQL_USER'])", "password=$($local['MYSQL_PASSWORD'])",'host=127.0.0.1',"port=$($local['MYSQL_PORT'])", "database=$($local['MYSQL_DATABASE'])",'default-character-set=utf8mb4'), [System.Text.UTF8Encoding]::new($false))
    $query = @'
SELECT VERSION(), @@port, DATABASE(), CURRENT_USER(), @@character_set_server, @@session.time_zone;
CREATE TEMPORARY TABLE phase0_probe (id BIGINT PRIMARY KEY, text_value VARCHAR(100) CHARACTER SET utf8mb4);
INSERT INTO phase0_probe VALUES (1, 'Tiếng Anh – xin chào');
SELECT COUNT(*), text_value FROM phase0_probe GROUP BY text_value;
DROP TEMPORARY TABLE phase0_probe;
SHOW SESSION STATUS LIKE 'Ssl_cipher';
'@
    $result = @(& $client "--defaults-extra-file=$options" --batch --skip-column-names "--execute=$query")
    if ($LASTEXITCODE -ne 0) { throw 'Application-user MySQL smoke test failed.' }
    $columns = $result[0] -split "`t"
    if ($columns[0] -notmatch '^8\.4\.' -or $columns[1] -ne $local['MYSQL_PORT']) { throw 'Unexpected server/version.' }
    if ($columns[5] -ne '+00:00') { throw 'Local server/session timezone must be UTC.' }
    if ($result[1] -notmatch '^1\s+Tiếng Anh') { throw 'Unicode temporary write/read did not match.' }
    $report = [ordered]@{
        recorded_at = [DateTime]::UtcNow.ToString('o')
        status = 'PASS'
        version = $columns[0]
        port = [int]$columns[1]
        database = $columns[2]
        application_account = $columns[3]
        charset = $columns[4]
        session_time_zone = $columns[5]
        temporary_unicode_write_read = 'PASS'
        tls_cipher = ($result[2] -split "`t")[1]
        domain_tables_created = $false
    }
    $report | ConvertTo-Json | Set-Content (Join-Path $runtime 'mysql-verification.json') -Encoding utf8
    Write-Output "MySQL $($columns[0]) on port $($columns[1]): application login, temporary Unicode write/read, UTC and TLS checked. No domain tables created."
} finally {
    if (Test-Path -LiteralPath $options) { Remove-Item -LiteralPath $options }
}
