$ErrorActionPreference = 'Stop'
$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
. (Join-Path $workspace 'scripts/phase0/local-env.ps1')
$config = Read-ProjectEnv -Path (Join-Path $workspace '.env')
$runtime = Join-Path $workspace '.runtime/phase1'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null
$database = 'phase1_auth_' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmss')
$mysql = Join-Path $workspace '.runtime/phase0/tools/mysql-8.4.13-winx64/bin/mysql.exe'
$adminFile = Join-Path $workspace '.runtime/phase0/mysql-admin.cnf'
if ($config['MYSQL_USER'] -notmatch '^[a-zA-Z0-9_]+$') { throw 'Invalid local database account name.' }
& $mysql "--defaults-extra-file=$adminFile" --execute="CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON $database.* TO '$($config['MYSQL_USER'])'@'127.0.0.1';"
if ($LASTEXITCODE -ne 0) { throw 'Could not create isolated test database.' }
[System.IO.File]::WriteAllText((Join-Path $runtime 'test-database.txt'), $database)
# Keep test settings out of the caller's subsequent application runs.
$settings = @{
    SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3307/$database`?connectionTimeZone=UTC"
    SPRING_DATASOURCE_USERNAME = $config['SPRING_DATASOURCE_USERNAME']
    SPRING_DATASOURCE_PASSWORD = $config['SPRING_DATASOURCE_PASSWORD']
    PHASE1_MYSQL_TEST = 'true'
    ADMIN_BOOTSTRAP_ENABLED = 'false'
    MAVEN_USER_HOME = (Join-Path $workspace '.runtime/maven')
    MAVEN_OPTS = "-Xms32m -Xmx256m -Dmaven.repo.local=$workspace/.runtime/m2"
}
$previous = @{}
foreach ($key in $settings.Keys) {
    $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
    [Environment]::SetEnvironmentVariable($key, $settings[$key], 'Process')
}
Push-Location $workspace
try {
    & backend/mvnw.cmd -f backend/pom.xml verify "-DargLine=-Xms64m -Xmx384m"
    if ($LASTEXITCODE -ne 0) { throw 'MySQL integration checks failed.' }
} finally {
    Pop-Location
    foreach ($key in $previous.Keys) {
        if ($null -eq $previous[$key]) { Remove-Item -LiteralPath "Env:$key" -ErrorAction SilentlyContinue }
        else { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    }
}
Write-Output "PASS: migration and HTTP/JDBC checks on $database. Test database retained; no existing database was reset."
