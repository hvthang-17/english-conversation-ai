# Windows PowerShell 5.1 and PowerShell 7.
[CmdletBinding()]
param([switch]$Live)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
. (Join-Path $root 'scripts/phase0/local-env.ps1')

$format = 'audio-24khz-48kbitrate-mono-mp3'
$samples = [ordered]@{
    a2 = '<voice name="en-US-AriaNeural">Excuse me, where is the train station?</voice>'
    b1 = '<voice name="en-US-GuyNeural">Is breakfast included in the room price?</voice><voice name="en-US-AriaNeural">Yes, but this rate is non-refundable. If your plans might change, I can offer a flexible rate.</voice>'
}
foreach ($sample in @($samples.Keys)) {
    $payload = '<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="en-US">' + $samples[$sample] + '</speak>'
    [xml]$ssml = $payload
    $voiceCount = if ($sample -eq 'a2') { 1 } else { 2 }
    if ($ssml.DocumentElement.ChildNodes.Count -ne $voiceCount) { throw 'Invalid SSML voice count.' }
    $samples[$sample] = $payload
}

$settings = Read-ProjectEnv -Path (Join-Path $root '.env')
$key = $env:AZURE_SPEECH_KEY
$region = $env:AZURE_SPEECH_REGION
if (-not $key) { $key = $settings['AZURE_SPEECH_KEY'] }
if (-not $region) { $region = $settings['AZURE_SPEECH_REGION'] }
$configured = -not [string]::IsNullOrWhiteSpace($key) -and $region -cmatch '^[a-z][a-z0-9]{1,30}$'
if (-not $Live) {
    [pscustomobject]@{ mode = 'offline'; samples = $samples.Count; format = $format; configured = $configured; requests = 0 }
    return
}
if (-not $configured) { throw 'Set AZURE_SPEECH_KEY and AZURE_SPEECH_REGION for the same Speech resource.' }

$endpoint = "https://$region.tts.speech.microsoft.com/cognitiveservices/v1"
$folder = Join-Path $root ('.runtime/g4-azure-tts-' + [guid]::NewGuid().ToString('N'))
Add-Type -AssemblyName System.Net.Http
$handler = [Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$client = [Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds(20)

try {
    $client.DefaultRequestHeaders.Add('Ocp-Apim-Subscription-Key', $key)
    $client.DefaultRequestHeaders.Add('X-Microsoft-OutputFormat', $format)
    $client.DefaultRequestHeaders.UserAgent.ParseAdd('EnglishConversationG4Probe')

    foreach ($sample in $samples.GetEnumerator()) {
        $timer = [Diagnostics.Stopwatch]::StartNew()
        $content = [Net.Http.StringContent]::new($sample.Value, [Text.Encoding]::UTF8, 'application/ssml+xml')
        try {
            try {
                $response = $client.PostAsync($endpoint, $content).GetAwaiter().GetResult()
            } catch {
                throw "Azure TTS connection failed for $($sample.Key). Check the network and region."
            }
            try {
                $status = [int]$response.StatusCode
                if ($status -ne 200) { throw "Azure TTS returned HTTP $status for $($sample.Key)." }
                $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
                if ([string]$response.Content.Headers.ContentType -notmatch '^audio/mpeg(?:\s*;|$)' -or $bytes.Length -eq 0) {
                    throw "Azure TTS did not return an MP3 for $($sample.Key)."
                }
            } finally {
                $response.Dispose()
            }
        } finally {
            $content.Dispose()
        }

        $timer.Stop()
        [void][IO.Directory]::CreateDirectory($folder)
        $path = Join-Path $folder "$($sample.Key).mp3"
        [IO.File]::WriteAllBytes($path, $bytes)
        [pscustomobject]@{ sample = $sample.Key; elapsedMs = $timer.ElapsedMilliseconds; bytes = $bytes.Length; file = $path }
    }
} finally {
    $client.Dispose()
}
