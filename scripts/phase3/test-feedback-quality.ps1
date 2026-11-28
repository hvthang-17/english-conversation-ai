[CmdletBinding(DefaultParameterSetName = 'Run')]
param(
    [Parameter(ParameterSetName = 'Run')][switch]$Live,
    [Parameter(ParameterSetName = 'Run')][switch]$CompareV32,
    [Parameter(ParameterSetName = 'Run')][string]$Model,
    [Parameter(ParameterSetName = 'Run')][string[]]$Cases = @(),
    [Parameter(ParameterSetName = 'Run')][ValidateRange(0, 64)][int]$MaxRequests = 0,
    [Parameter(ParameterSetName = 'Run')][ValidateRange(1, 60)][int]$RequestsPerMinute = 6,
    [Parameter(Mandatory, ParameterSetName = 'Review')][string]$ReviewDirectory
)

$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8 = [Text.UTF8Encoding]::new($false)

function Protect-CsvCell([string]$Value) {
    if ($Value -match '^\s*[=+@-]' -or $Value -match '^[\t\r\n]') { return "'$Value" }
    return $Value
}

function New-ReviewRow($Result, [string]$Kind, [string]$Suffix, [string]$Evidence) {
    $sample = $Result.sample
    [pscustomobject][ordered]@{
        ItemId = "$($Result.promptVersion)/$($sample.id)/$Suffix"
        Prompt = $Result.promptVersion
        Case = $sample.id
        Level = $sample.level
        Kind = $Kind
        Question = Protect-CsvCell $sample.question
        Answer = Protect-CsvCell $sample.answer
        Expected = Protect-CsvCell $sample.expectation
        Evidence = Protect-CsvCell $Evidence
        ClassificationCorrect = ''
        KeepsMeaning = ''
        Useful = ''
        VocabularyCorrect = ''
        FalseError = ''
        SevereIssue = ''
        Notes = ''
    }
}

function Get-ReviewRows($Report) {
    foreach ($result in $Report.results) {
        if ($null -eq $result.feedback) { continue }
        $feedback = $result.feedback
        $turn = $feedback.coaching.turns[0]
        $turnEvidence = "$($turn.status) / $($turn.questionQuality) | $($turn.explanationVi) | $($feedback.summary) | $($feedback.goalAssessment) | $($feedback.strengths -join ', ') | $($feedback.nextPractice)"
        New-ReviewRow $result 'TURN' 'turn' $turnEvidence
        if ($turn.suggestionEn) {
            $kind = if ($turn.adviceKind -eq 'CLARIFY') { 'CLARIFICATION' } else { 'ADVICE' }
            New-ReviewRow $result $kind 'advice' "$($turn.adviceKind) | $($turn.suggestionEn)"
        }
        $index = 0
        foreach ($correction in $feedback.corrections) {
            $index++
            $kind = if ($correction.itemType -eq 'ERROR') { 'ERROR' } else { 'CORRECTION' }
            $evidence = "$($correction.ruleCode) | $($correction.originalText) -> $($correction.suggestedText) | $($correction.explanationVi)"
            New-ReviewRow $result $kind "correction-$index" $evidence
        }
        $index = 0
        foreach ($suggestion in $feedback.suggestions) {
            $index++
            $source = $suggestion.source
            $evidence = "$($source.kind) | $($suggestion.expression) [$($suggestion.partOfSpeech)] | $($suggestion.meaningVi) | $($suggestion.exampleEn) | $($source.excerpt) | $($source.useVi) | meaningVerified=$($source.meaningVerified)"
            New-ReviewRow $result 'VOCABULARY' "word-$index" $evidence
        }
        if ($feedback.coaching.replay) {
            $replay = $feedback.coaching.replay
            New-ReviewRow $result 'REPLAY' 'replay' "$($replay.criterionVi) | $($replay.transferQuestionEn)"
        }
    }
}

function Write-ReviewCsv([string]$Path, [object[]]$Rows) {
    $csv = $Rows | ConvertTo-Csv -NoTypeInformation
    [IO.File]::WriteAllLines($Path, [string[]]$csv, [Text.UTF8Encoding]::new($true))
}

function Measure-ReviewColumn([object[]]$Rows, [string]$Column, [string[]]$Kinds = @()) {
    $applicable = @($Rows | Where-Object { $Kinds.Count -eq 0 -or $_.Kind -in $Kinds })
    $yes = @($applicable | Where-Object { $_.$Column -eq 'YES' }).Count
    $no = @($applicable | Where-Object { $_.$Column -eq 'NO' }).Count
    [ordered]@{ applicable = $applicable.Count; reviewed = $yes + $no; yes = $yes; no = $no }
}

if ($PSCmdlet.ParameterSetName -eq 'Review') {
    $directory = (Resolve-Path -LiteralPath $ReviewDirectory).Path
    $report = Get-Content -LiteralPath (Join-Path $directory 'report.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $expected = @(Get-ReviewRows $report)
    if ($expected.Count -eq 0) { throw 'This run has no validated feedback to review.' }
    $worksheet = Join-Path $directory 'review.csv'
    if (-not (Test-Path -LiteralPath $worksheet)) {
        Write-ReviewCsv $worksheet $expected
        Write-Output "Review worksheet: $worksheet"
        return
    }
    $actual = @(Import-Csv -LiteralPath $worksheet -Encoding UTF8)
    $byId = @{}
    foreach ($row in $actual) {
        if ([string]::IsNullOrWhiteSpace($row.ItemId) -or $byId.ContainsKey($row.ItemId)) {
            throw 'review.csv has a missing or duplicate ItemId.'
        }
        $byId[$row.ItemId] = $row
    }
    if ($byId.Count -ne $expected.Count) { throw 'Keep all ItemId rows from the original review.csv.' }
    $scored = foreach ($item in $expected) {
        if (-not $byId.ContainsKey($item.ItemId)) { throw 'review.csv has a missing or changed ItemId.' }
        $row = $byId[$item.ItemId]
        foreach ($column in 'ClassificationCorrect', 'KeepsMeaning', 'Useful', 'VocabularyCorrect', 'FalseError', 'SevereIssue') {
            $value = ([string]$row.$column).Trim()
            if ($value -notin '', 'YES', 'NO') { throw "Use YES, NO or blank in $column at $($item.ItemId)." }
            $item.$column = $value
        }
        $item
    }
    $scores = foreach ($summary in $report.summaries) {
        $rows = @($scored | Where-Object Prompt -eq $summary.promptVersion)
        $metrics = [ordered]@{
            classificationCorrect = Measure-ReviewColumn $rows 'ClassificationCorrect' @('TURN')
            keepsMeaning = Measure-ReviewColumn $rows 'KeepsMeaning' @('ADVICE', 'ERROR', 'CORRECTION')
            useful = Measure-ReviewColumn $rows 'Useful'
            vocabularyCorrect = Measure-ReviewColumn $rows 'VocabularyCorrect' @('VOCABULARY')
            falseErrors = Measure-ReviewColumn $rows 'FalseError' @('ERROR')
            severeIssues = Measure-ReviewColumn $rows 'SevereIssue'
        }
        $missing = @($metrics.Values | Where-Object { $_.reviewed -lt $_.applicable }).Count -gt 0
        [ordered]@{
            promptVersion = $summary.promptVersion
            validatedTurns = $summary.validated
            targetTurns = $summary.targets
            reviewComplete = ($rows.Count -gt 0 -and -not $missing)
            all32TurnsValidated = ($summary.targets -eq 32 -and $summary.validated -eq 32)
            metrics = $metrics
        }
    }
    $scorePath = Join-Path $directory 'review-summary.json'
    $score = [ordered]@{
        mode = $report.mode
        model = $report.model
        fixtureSha256 = $report.fixtureSha256
        prompts = @($scores)
    }
    [IO.File]::WriteAllText($scorePath, ($score | ConvertTo-Json -Depth 8), $utf8)
    Write-Output "Reviewer metrics: $scorePath"
    return
}

$caseIds = @(
    foreach ($caseId in $Cases) {
        if ($caseId -notmatch '^[0-9]{1,2}$' -or [int]$caseId -lt 1 -or [int]$caseId -gt 32) {
            throw 'Use case IDs from 01 to 32.'
        }
        '{0:00}' -f [int]$caseId
    }
)
if (@($caseIds | Sort-Object -Unique).Count -ne $caseIds.Count) { throw 'Case IDs must be unique.' }
$targets = if ($caseIds.Count -gt 0) { $caseIds.Count } else { 32 }
$planned = if ($CompareV32) { 2 * $targets } else { $targets }
$timeout = 30
$contextLimit = 80000
$key = $null
if ($Live) {
    if ($MaxRequests -lt $planned) {
        throw "The selected cases require $planned Gemini requests. Set -MaxRequests or select fewer -Cases."
    }
    . (Join-Path $PSScriptRoot '../phase0/local-env.ps1')
    $config = Read-ProjectEnv -Path (Join-Path $workspace '.env')
    $key = if ($env:GEMINI_API_KEY) { $env:GEMINI_API_KEY } else { $config['GEMINI_API_KEY'] }
    if (-not $Model) {
        $Model = if ($env:GEMINI_CHAT_MODEL) { $env:GEMINI_CHAT_MODEL } else { $config['GEMINI_CHAT_MODEL'] }
    }
    if ($key -notmatch '^[a-zA-Z0-9_-]{20,256}$') { throw 'Configure GEMINI_API_KEY in .env or the process environment.' }
    if ($Model -notmatch '^[a-zA-Z0-9._-]{1,120}$' -or $Model.Contains($key)) {
        throw 'Configure GEMINI_CHAT_MODEL or pass a model identifier with -Model.'
    }
    $timeoutValue = if ($env:CHAT_TIMEOUT_SECONDS) { $env:CHAT_TIMEOUT_SECONDS } else { $config['CHAT_TIMEOUT_SECONDS'] }
    $contextValue = if ($env:CHAT_CONTEXT_LIMIT) { $env:CHAT_CONTEXT_LIMIT } else { $config['CHAT_CONTEXT_LIMIT'] }
    if ($timeoutValue -and (-not [int]::TryParse($timeoutValue, [ref]$timeout) -or $timeout -lt 1 -or $timeout -gt 60)) {
        throw 'CHAT_TIMEOUT_SECONDS must be an integer from 1 to 60.'
    }
    if ($contextValue -and (-not [int]::TryParse($contextValue, [ref]$contextLimit) -or $contextLimit -lt 1000 -or $contextLimit -gt 100000)) {
        throw 'CHAT_CONTEXT_LIMIT must be an integer from 1000 to 100000.'
    }
    Write-Output "Live FEEDBACK: model $Model, $planned planned, cap $MaxRequests, $RequestsPerMinute requests/minute."
} else {
    $Model = ''
    Write-Output "Offline fixtures: $targets cases, zero Gemini requests."
}

$runId = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$output = Join-Path $workspace ".runtime/feedback-quality/$runId"
[IO.Directory]::CreateDirectory($output) | Out-Null
$settings = @{
    live = [bool]$Live
    compareV32 = [bool]$CompareV32
    model = [string]$Model
    caseIds = @($caseIds)
    maxRequests = $MaxRequests
    requestsPerMinute = $RequestsPerMinute
    timeoutSeconds = $timeout
    contextLimit = $contextLimit
}
[IO.File]::WriteAllText((Join-Path $output 'settings.json'), ($settings | ConvertTo-Json), $utf8)
Write-Output "Evidence directory: $output"

$maven = Get-Command mvn -ErrorAction SilentlyContinue
$mavenBin = if ($maven) { $maven.Source } else { Join-Path $workspace 'backend/mvnw.cmd' }
$java = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    Join-Path $env:JAVA_HOME 'bin/java.exe'
} else {
    (Get-Command java -ErrorAction Stop).Source
}
$pom = Join-Path $workspace 'backend/pom.xml'
$classpathFile = Join-Path $output 'classpath.tmp'
$reportPath = Join-Path $output 'report.json'
$priorKey = [Environment]::GetEnvironmentVariable('FB_ACCEPTANCE_API_KEY', 'Process')
try {
    $mavenArgs = @('--batch-mode', '--quiet', '--no-transfer-progress', '-f', $pom, 'compile',
        'org.apache.maven.plugins:maven-dependency-plugin:3.10.0:build-classpath',
        '-DincludeScope=runtime', "-Dmdep.outputFile=$classpathFile")
    & $mavenBin @mavenArgs
    if ($LASTEXITCODE -ne 0) { throw 'Backend compilation or classpath setup failed. No Gemini request was sent.' }

    $classPath = (Join-Path $workspace 'backend/target/classes') + [IO.Path]::PathSeparator + [IO.File]::ReadAllText($classpathFile).Trim()
    [Environment]::SetEnvironmentVariable('FB_ACCEPTANCE_API_KEY', $key, 'Process')
    $runner = Join-Path $PSScriptRoot 'FeedbackQualityAcceptance.java'
    & $java '-Dfile.encoding=UTF-8' --class-path $classPath --source 21 $runner $workspace $output
    $runExit = $LASTEXITCODE
} finally {
    [Environment]::SetEnvironmentVariable('FB_ACCEPTANCE_API_KEY', $priorKey, 'Process')
    if (Test-Path -LiteralPath $classpathFile) { Remove-Item -LiteralPath $classpathFile }
}

if (Test-Path -LiteralPath $reportPath) {
    $report = Get-Content -LiteralPath $reportPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $rows = @(Get-ReviewRows $report)
    if ($rows.Count -gt 0) {
        Write-ReviewCsv (Join-Path $output 'review.csv') $rows
    }
    foreach ($summary in $report.summaries) {
        Write-Output "$($summary.promptVersion): $($summary.validated)/$($summary.targets) validated, $($summary.matchingLabels)/$($summary.targets) matching labels."
    }
    Write-Output "Gemini attempts: $($report.requestsAttempted)/$($report.requestsPlanned)"
    if ($report.stopReason) { Write-Output "Stopped: $($report.stopReason)" }
}
if ($runExit -ne 0) { exit $runExit }
