param(
    [string]$Jar = "opentools-app\target\dist\opentools.jar",
    [int]$TableRows = 50000,
    [int]$OverlapRecords = 50000,
    [int]$Iterations = 3,
    [switch]$Keep
)

$ErrorActionPreference = "Stop"
$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$jarPath = (Resolve-Path (Join-Path $workspace $Jar)).Path
$verification = Join-Path $workspace ".verification\benchmark"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$benchmarkDir = Join-Path $verification "spool-$stamp"
$project = Join-Path $benchmarkDir "project"
New-Item -ItemType Directory -Force -Path $project | Out-Null

$java = if ($env:JAVA_HOME) {
    Join-Path $env:JAVA_HOME "bin\java.exe"
} else {
    "java.exe"
}

function Get-Median([double[]]$Values) {
    $sorted = @($Values | Sort-Object)
    $middle = [Math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) {
        return $sorted[$middle]
    }
    return ($sorted[$middle - 1] + $sorted[$middle]) / 2.0
}

function Get-TemporarySqliteBytes {
    $runs = Join-Path $project "runs"
    if (-not (Test-Path -LiteralPath $runs)) {
        return 0
    }
    $files = @(Get-ChildItem -LiteralPath $runs -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "*.sqlite*" })
    if ($files.Count -eq 0) {
        return 0
    }
    return [long](($files | Measure-Object -Property Length -Sum).Sum)
}

function Invoke-MeasuredTask {
    param(
        [string]$Name,
        [string[]]$Arguments
    )
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $java
    $startInfo.WorkingDirectory = $workspace
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.ArgumentList.Add("-jar")
    $startInfo.ArgumentList.Add($jarPath)
    foreach ($argument in $Arguments) {
        $startInfo.ArgumentList.Add($argument)
    }

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $process = [System.Diagnostics.Process]::Start($startInfo)
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $peakWorkingSet = 0L
    $peakTempBytes = 0L
    while (-not $process.HasExited) {
        try {
            $process.Refresh()
            $peakWorkingSet = [Math]::Max(
                $peakWorkingSet,
                [long]$process.PeakWorkingSet64
            )
        } catch {
            # Process metrics may briefly be unavailable during exit.
        }
        $peakTempBytes = [Math]::Max(
            $peakTempBytes,
            (Get-TemporarySqliteBytes)
        )
        Start-Sleep -Milliseconds 100
    }
    $process.WaitForExit()
    $stopwatch.Stop()
    try {
        $process.Refresh()
        $peakWorkingSet = [Math]::Max(
            $peakWorkingSet,
            [long]$process.PeakWorkingSet64
        )
    } catch {
        # Keep the last observed value.
    }
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    if ($process.ExitCode -ne 0) {
        throw "$Name failed with exit code $($process.ExitCode): $stderr $stdout"
    }
    return [pscustomobject]@{
        name = $Name
        milliseconds = [Math]::Round($stopwatch.Elapsed.TotalMilliseconds, 1)
        peakWorkingSetBytes = $peakWorkingSet
        peakWorkingSetMiB = [Math]::Round(
            $peakWorkingSet / 1MB,
            1
        )
        peakTempSqliteBytes = $peakTempBytes
        peakTempSqliteMiB = [Math]::Round(
            $peakTempBytes / 1MB,
            1
        )
        stdout = $stdout
        stderr = $stderr
    }
}

try {
    $table = Join-Path $project "spool-table.tsv"
    $headers = @("id") + @(1..10 | ForEach-Object { "c$_" })
    $writer = [System.IO.StreamWriter]::new(
        $table,
        $false,
        [System.Text.UTF8Encoding]::new($false),
        1048576
    )
    try {
        $writer.WriteLine($headers -join "`t")
        $payload = "x" * 40
        for ($index = 0; $index -lt $TableRows; $index++) {
            $values = @("row_$index")
            for ($column = 1; $column -le 10; $column++) {
                $values += "$payload`_$($index % 1000)"
            }
            $writer.WriteLine($values -join "`t")
        }
    } finally {
        $writer.Dispose()
    }

    $annotation = Join-Path $project "spool-overlap.gff3"
    $writer = [System.IO.StreamWriter]::new(
        $annotation,
        $false,
        [System.Text.UTF8Encoding]::new($false),
        1048576
    )
    try {
        $writer.WriteLine("##gff-version 3")
        for ($index = 0; $index -lt $OverlapRecords; $index++) {
            $start = $index * 10L + 1
            $end = $start + 4
            $writer.WriteLine(
                "chr1`tbenchmark`texon`t$start`t$end`t.`t+`t.`tID=feature_$index"
            )
        }
    } finally {
        $writer.Dispose()
    }

    $transposeRuns = @()
    $overlapRuns = @()
    for ($iteration = 0; $iteration -lt $Iterations; $iteration++) {
        $transposeOutput = Join-Path $project "transpose-$iteration.tsv"
        $transposeRuns += Invoke-MeasuredTask `
            -Name "table.transpose" `
            -Arguments @(
                "--project", $project,
                "run", "table.transpose",
                "--param", "input=$table",
                "--param", "transposeMode=DISK",
                "--param", "maxTempMegabytes=8192",
                "--param", "output=$transposeOutput",
                "--json"
            )

        $overlapOutput = Join-Path $project "overlap-$iteration.tsv"
        $overlapRuns += Invoke-MeasuredTask `
            -Name "gxf.overlap" `
            -Arguments @(
                "--project", $project,
                "run", "gxf.overlap",
                "--param", "input=$annotation",
                "--param", "overlapMode=DISK",
                "--param", "maxTempMegabytes=8192",
                "--param", "maxPairs=1000",
                "--param", "output=$overlapOutput",
                "--json"
            )
    }

    $javaVersion = (& $java -version 2>&1 |
        ForEach-Object { $_.ToString() } |
        Select-Object -First 1)
    $transposeTimes = @($transposeRuns | ForEach-Object { $_.milliseconds })
    $overlapTimes = @($overlapRuns | ForEach-Object { $_.milliseconds })
    $report = [ordered]@{
        generatedAt = (Get-Date).ToString("o")
        java = $javaVersion
        iterations = $Iterations
        tableRows = $TableRows
        tableColumns = 11
        tableBytes = (Get-Item -LiteralPath $table).Length
        overlapRecords = $OverlapRecords
        overlapBytes = (Get-Item -LiteralPath $annotation).Length
        transpose = [ordered]@{
            milliseconds = $transposeTimes
            medianMilliseconds = [Math]::Round(
                (Get-Median $transposeTimes),
                1
            )
            outputBytes = (Get-Item -LiteralPath $transposeOutput).Length
            peakWorkingSetMiB = @(
                $transposeRuns | ForEach-Object { $_.peakWorkingSetMiB }
            )
            peakTempSqliteMiB = @(
                $transposeRuns | ForEach-Object { $_.peakTempSqliteMiB }
            )
        }
        overlap = [ordered]@{
            milliseconds = $overlapTimes
            medianMilliseconds = [Math]::Round(
                (Get-Median $overlapTimes),
                1
            )
            outputBytes = (Get-Item -LiteralPath $overlapOutput).Length
            peakWorkingSetMiB = @(
                $overlapRuns | ForEach-Object { $_.peakWorkingSetMiB }
            )
            peakTempSqliteMiB = @(
                $overlapRuns | ForEach-Object { $_.peakTempSqliteMiB }
            )
        }
        note = "Single-machine process-level smoke test; JVM/CLI startup and output writing are included. Temp SQLite sampling is 100 ms."
    }
    $reportPath = Join-Path $verification "spool-latest.json"
    $report | ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath $reportPath -Encoding UTF8
    $report | ConvertTo-Json -Depth 8
    Write-Output "Report: $reportPath"
    if ($Keep) {
        Write-Output "Artifacts: $benchmarkDir"
    }
} finally {
    if (-not $Keep) {
        $resolved = (Resolve-Path $benchmarkDir).Path
        $verificationRoot = (Resolve-Path $verification).Path
        $inside = $resolved.StartsWith(
            $verificationRoot,
            [System.StringComparison]::OrdinalIgnoreCase
        )
        if (-not $inside -or $resolved -eq $verificationRoot) {
            throw "Refusing to remove unexpected benchmark path: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
