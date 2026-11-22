$ErrorActionPreference = 'Stop'
$workspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runtime = Join-Path $workspace '.runtime/phase0'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$audio = Join-Path $runtime 'synthetic-utterance.wav'
$utterance = 'I would like a bowl of vegetable soup and a glass of water, please.'
$voice = New-Object -ComObject SAPI.SpVoice
$stream = New-Object -ComObject SAPI.SpFileStream
try {
    $english = @($voice.GetVoices() | Where-Object { $_.GetAttribute('Language') -match '(^|;)409($|;)' })
    if ($english.Count -gt 0) { $voice.Voice = $english[0] }
    $stream.Open($audio, 3, $false)
    $voice.AudioOutputStream = $stream
    [void]$voice.Speak($utterance)
    $stream.Close()
    $manifest = [ordered]@{
        source = 'WINDOWS_SAPI_SYNTHETIC'
        transcript = $utterance
        sha256 = (Get-FileHash -LiteralPath $audio -Algorithm SHA256).Hash.ToLowerInvariant()
        generated_at = [DateTime]::UtcNow.ToString('o')
        personal_audio = $false
        uploaded = $false
    }
    $manifest | ConvertTo-Json | Set-Content (Join-Path $runtime 'synthetic-audio.json') -Encoding utf8
    Write-Output 'Generated local synthetic speech fixture and provenance manifest. Nothing uploaded.'
} finally {
    [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($stream)
    [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($voice)
}
