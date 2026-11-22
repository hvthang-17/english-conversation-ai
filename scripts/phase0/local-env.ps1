Set-StrictMode -Version Latest

function Read-ProjectEnv {
    param([Parameter(Mandatory)][string]$Path)
    $values = @{}
    if (Test-Path -LiteralPath $Path) {
        foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
            if ($line -match '^\s*([A-Z][A-Z0-9_]*)\s*=(.*)$') {
                $value = $Matches[2].Trim()
                if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                    $value = $value.Substring(1, $value.Length - 2)
                }
                $values[$Matches[1]] = $value
            }
        }
    }
    return $values
}

function Import-ProjectEnv {
    param([Parameter(Mandatory)][string]$Path)
    $values = Read-ProjectEnv -Path $Path
    foreach ($key in $values.Keys) {
        if (-not [string]::IsNullOrWhiteSpace($values[$key])) {
            [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process')
        }
    }
}
