$ErrorActionPreference = 'Stop'
$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runtime = Join-Path $workspace '.runtime/phase2'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null
$stamp = [DateTime]::UtcNow.ToString('yyyyMMddHHmmss')
$database = "phase2_auth_$stamp"
$upgrade = "${database}_upgrade"
$username = "phase2_$stamp"
$password = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
$mysql = Join-Path $workspace '.runtime/phase0/tools/mysql-8.4.13-winx64/bin/mysql.exe'
$adminFile = Join-Path $workspace '.runtime/phase0/mysql-admin.cnf'
$sql = "CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE DATABASE $upgrade CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '$username'@'127.0.0.1' IDENTIFIED BY '$password'; GRANT ALL PRIVILEGES ON $database.* TO '$username'@'127.0.0.1'; GRANT ALL PRIVILEGES ON $upgrade.* TO '$username'@'127.0.0.1';"
$sql | & $mysql "--defaults-extra-file=$adminFile"
if ($LASTEXITCODE -ne 0) { throw 'Could not prepare isolated test schemas.' }
# Private runtime fixture for optional browser smoke; never print or commit.
@{ database=$database; upgrade=$upgrade; username=$username; password=$password } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime 'test-connection.json')
$settings = @{
 SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3307/$database`?connectionTimeZone=UTC"
 SPRING_DATASOURCE_USERNAME=$username
 SPRING_DATASOURCE_PASSWORD=$password
 PHASE1_MYSQL_TEST='true'
 PHASE2_UPGRADE_URL="jdbc:mysql://127.0.0.1:3307/$upgrade`?connectionTimeZone=UTC"
 ADMIN_BOOTSTRAP_ENABLED='false'
 MAVEN_USER_HOME=(Join-Path $workspace '.runtime/maven')
 MAVEN_OPTS="-Xms32m -Xmx256m -Dmaven.repo.local=$workspace/.runtime/m2"
}
$previous = @{}
foreach ($key in $settings.Keys) {
 $previous[$key]=[Environment]::GetEnvironmentVariable($key,'Process')
 [Environment]::SetEnvironmentVariable($key,$settings[$key],'Process')
}
Push-Location $workspace
try {
 & backend/mvnw.cmd -f backend/pom.xml verify "-DargLine=-Xms64m -Xmx384m"
 if ($LASTEXITCODE -ne 0) { throw 'Phase 2 MySQL checks failed.' }
} finally {
 Pop-Location
 foreach ($key in $previous.Keys) {
  if ($null -eq $previous[$key]) { Remove-Item -LiteralPath "Env:$key" -ErrorAction SilentlyContinue }
  else { [Environment]::SetEnvironmentVariable($key,$previous[$key],'Process') }
 }
}
Write-Output "PASS: phase2 fresh/upgrade schemas retained: $database and $upgrade. No existing database reset."
