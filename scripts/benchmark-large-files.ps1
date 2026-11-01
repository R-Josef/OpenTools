param(
    [string]$Jar = "opentools-app\target\dist\opentools.jar",
    [int]$FastaRecords = 200000,
    [int]$TableRows = 200000,
    [switch]$Keep
)

$ErrorActionPreference = "Stop"
$root = (Get-Location).Path
$verification = Join-Path $root ".verification\benchmark"
$runRoot = Join-Path $verification ([guid]::NewGuid().ToString("N"))
$project = Join-Path $runRoot "project"
New-Item -ItemType Directory -Force -Path $project | Out-Null

function Invoke-TimedJson {
    param([string[]]$Arguments)
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $stdoutPath = Join-Path $runRoot ("stdout-" + [guid]::NewGuid().ToString("N") + ".json")
    $stderrPath = Join-Path $runRoot ("stderr-" + [guid]::NewGuid().ToString("N") + ".log")
    $process = Start-Process -FilePath (Join-Path $env:JAVA_HOME "bin\java.exe") `
        -ArgumentList (@("-jar", (Resolve-Path $Jar).Path) + $Arguments) `
        -WorkingDirectory $root `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath `
        -Wait `
        -PassThru
    $stopwatch.Stop()
    $text = if (Test-Path $stdoutPath) { Get-Content -Raw $stdoutPath } else { "" }
    [pscustomobject]@{
        exitCode = $process.ExitCode
        elapsedMs = [math]::Round($stopwatch.Elapsed.TotalMilliseconds, 1)
        stdout = $text
        stderr = if (Test-Path $stderrPath) { Get-Content -Raw $stderrPath } else { "" }
    }
}

try {
    $fasta = Join-Path $project "benchmark.fa"
    $sequence = ("ACGT" * 20)
    $writer = [System.IO.StreamWriter]::new($fasta, $false, [System.Text.UTF8Encoding]::new($false), 1048576)
    try {
        for ($i = 0; $i -lt $FastaRecords; $i++) {
            $writer.WriteLine(">sequence_$i")
            $writer.WriteLine($sequence)
        }
    } finally {
        $writer.Dispose()
    }

    $table = Join-Path $project "benchmark.tsv"
    $writer = [System.IO.StreamWriter]::new($table, $false, [System.Text.UTF8Encoding]::new($false), 1048576)
    try {
        $writer.WriteLine("id`tgroup`tvalue`tnote")
        for ($i = 0; $i -lt $TableRows; $i++) {
            $writer.WriteLine("$i`tgroup_$($i % 100)`t$($i % 1000)`tbenchmark")
        }
    } finally {
        $writer.Dispose()
    }

    $tools = Invoke-TimedJson @("--project", $project, "tools", "list", "--json")
    $fastaRun = Invoke-TimedJson @(
        "--project", $project,
        "run", "fasta.stats",
        "--param", "input=$fasta",
        "--json"
    )
    $tableRun = Invoke-TimedJson @(
        "--project", $project,
        "run", "table.preview",
        "--param", "input=$table",
        "--param", "previewRows=100",
        "--param", "countRows=true",
        "--json"
    )

    $report = [pscustomobject]@{
        generatedAt = (Get-Date).ToString("o")
        javaVersion = (& (Join-Path $env:JAVA_HOME "bin\java.exe") -version 2>&1 | Select-Object -First 1)
        jarBytes = (Get-Item $Jar).Length
        fastaRecords = $FastaRecords
        fastaBytes = (Get-Item $fasta).Length
        tableRows = $TableRows
        tableBytes = (Get-Item $table).Length
        startupToolsListMs = $tools.elapsedMs
        fastaStatsMs = $fastaRun.elapsedMs
        tablePreviewMs = $tableRun.elapsedMs
        exitCodes = @($tools.exitCode, $fastaRun.exitCode, $tableRun.exitCode)
        success = ($tools.exitCode -eq 0 -and $fastaRun.exitCode -eq 0 -and $tableRun.exitCode -eq 0)
    }
    $reportPath = Join-Path $verification "large-files-latest.json"
    $report | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 $reportPath
    $report | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 (Join-Path $runRoot "benchmark.json")
    $report | Format-List
    "Report: $reportPath"
    if ($Keep) {
        "Artifacts: $runRoot"
    }
} finally {
    if (-not $Keep) {
        $resolved = (Resolve-Path $runRoot).Path
        $verificationRoot = (Resolve-Path $verification).Path
        $inside = $resolved.StartsWith($verificationRoot, [System.StringComparison]::OrdinalIgnoreCase)
        if (-not $inside -or $resolved -eq $verificationRoot) {
            throw "Refusing to remove unexpected benchmark path: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
