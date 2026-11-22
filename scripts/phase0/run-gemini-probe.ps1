param([switch]$Live)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-env.ps1')
$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ($Live) { throw 'Live calls are not authorized for the current phase-0 run. Only offline preparation was requested.' }
Import-ProjectEnv -Path (Join-Path $workspace '.env')
$maven = Join-Path $workspace 'backend/mvnw.cmd'
$pom = Join-Path $workspace 'experiments/phase0/gemini/pom.xml'
& $maven -f $pom --batch-mode "-Dphase0.workspace=$workspace" verify exec:java
if ($LASTEXITCODE -ne 0) { throw 'Offline Gemini probe preparation failed.' }
