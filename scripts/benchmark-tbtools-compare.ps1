param(
    [string]$OpenToolsJar = "opentools-app\target\dist\opentools.jar",
    [string]$RpcUri = "http://127.0.0.1:8765/rpc",
    [int]$FastaRecords = 200000,
    [int]$TableRows = 100000,
    [int]$Iterations = 3,
    [switch]$ReuseInputs,
    [switch]$KeepOutputs
)

$ErrorActionPreference = "Stop"
$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$jarPath = (Resolve-Path (Join-Path $workspace $OpenToolsJar)).Path
$harnessPath = (Resolve-Path (Join-Path $PSScriptRoot "compare\TbtoolsCompareHarness.java")).Path
$benchmarkRoot = Join-Path $workspace ".verification\tbtools-compare"
$project = Join-Path $benchmarkRoot "opentools-project"
New-Item -ItemType Directory -Force -Path $benchmarkRoot, $project | Out-Null

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

function Get-RpcProcessId {
    $connection = Get-NetTCPConnection -State Listen -LocalPort 8765 -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $connection) {
        throw "TBtools RPC is not listening on port 8765."
    }
    return $connection.OwningProcess
}

function New-BenchmarkInputs {
    $fasta = Join-Path $benchmarkRoot "benchmark.fa"
    if (-not ($ReuseInputs -and (Test-Path -LiteralPath $fasta))) {
        $writer = [System.IO.StreamWriter]::new(
            $fasta,
            $false,
            [System.Text.UTF8Encoding]::new($false),
            1048576
        )
        try {
            $sequence = "ACGT" * 25
            for ($index = 0; $index -lt $FastaRecords; $index++) {
                $writer.WriteLine(">sequence_$index")
                $writer.WriteLine($sequence)
            }
        } finally {
            $writer.Dispose()
        }
    }

    $table = Join-Path $benchmarkRoot "benchmark.tsv"
    if (-not ($ReuseInputs -and (Test-Path -LiteralPath $table))) {
        $writer = [System.IO.StreamWriter]::new(
            $table,
            $false,
            [System.Text.UTF8Encoding]::new($false),
            1048576
        )
        try {
            $writer.WriteLine("id`tgroup`tvalue`tc1`tc2`tc3`tc4`tc5`tc6`tc7`tc8")
            $payload = "x" * 40
            for ($index = 0; $index -lt $TableRows; $index++) {
                $values = @("row_$index", "group_$($index % 100)", "$($index % 1000)")
                for ($column = 1; $column -le 8; $column++) {
                    $values += "$payload`_$($index % 1000)"
                }
                $writer.WriteLine($values -join "`t")
            }
        } finally {
            $writer.Dispose()
        }
    }

    return [pscustomobject]@{
        Fasta = $fasta
        Table = $table
    }
}

function Invoke-MeasuredProcess {
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
    foreach ($argument in $Arguments) {
        $startInfo.ArgumentList.Add($argument)
    }

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $process = [System.Diagnostics.Process]::Start($startInfo)
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $peakWorkingSet = 0L
    while (-not $process.HasExited) {
        try {
            $process.Refresh()
            $peakWorkingSet = [Math]::Max(
                $peakWorkingSet,
                [long]$process.PeakWorkingSet64
            )
        } catch {
            # Process metrics can disappear during shutdown.
        }
        Start-Sleep -Milliseconds 50
    }
    $process.WaitForExit()
    $stopwatch.Stop()
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    if ($process.ExitCode -ne 0) {
        throw "$Name failed with exit code $($process.ExitCode): $stderr $stdout"
    }
    return [pscustomobject]@{
        milliseconds = [Math]::Round($stopwatch.Elapsed.TotalMilliseconds, 1)
        peakWorkingSetMiB = [Math]::Round($peakWorkingSet / 1MB, 1)
        stdout = $stdout
        stderr = $stderr
    }
}

function Invoke-MeasuredTbtoolsRpc {
    param(
        [string]$Name,
        [string]$Method,
        [hashtable]$Params,
        [int]$ProcessId
    )
    $payload = @{
        jsonrpc = "2.0"
        id = [guid]::NewGuid().ToString()
        method = $Method
        params = $Params
    } | ConvertTo-Json -Depth 20 -Compress

    $client = [System.Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromMinutes(30)
    $content = [System.Net.Http.StringContent]::new(
        $payload,
        [System.Text.Encoding]::UTF8,
        "application/json"
    )
    $peakWorkingSet = 0L
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $task = $client.PostAsync($RpcUri, $content)
        while (-not $task.Wait(50)) {
            try {
                $current = Get-Process -Id $ProcessId -ErrorAction Stop
                $peakWorkingSet = [Math]::Max(
                    $peakWorkingSet,
                    [long]$current.WorkingSet64
                )
            } catch {
                # Keep the last observed value.
            }
        }
        $response = $task.Result
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $json = $body | ConvertFrom-Json
        if ($null -ne $json.error) {
            throw "$Name RPC error $($json.error.code): $($json.error.message) $($json.error.data)"
        }
        $stopwatch.Stop()
        try {
            $current = Get-Process -Id $ProcessId -ErrorAction Stop
            $peakWorkingSet = [Math]::Max($peakWorkingSet, [long]$current.WorkingSet64)
        } catch {
            # Keep the last observed value.
        }
        return [pscustomobject]@{
            milliseconds = [Math]::Round($stopwatch.Elapsed.TotalMilliseconds, 1)
            peakWorkingSetMiB = [Math]::Round($peakWorkingSet / 1MB, 1)
            result = $json.result
        }
    } finally {
        $content.Dispose()
        $client.Dispose()
    }
}

function Assert-Output([string]$Path, [string]$Name) {
    if (-not (Test-Path -LiteralPath $Path) -or (Get-Item -LiteralPath $Path).Length -eq 0) {
        throw "$Name did not create a non-empty output."
    }
}

$inputs = New-BenchmarkInputs
$rpcPid = Get-RpcProcessId
$workloads = @(
    [pscustomobject]@{
        Name = "fasta_stats"
        OpenToolsId = "fasta.stats"
        TbtoolsMethod = "FastaStat.process"
        Input = $inputs.Fasta
        TbtoolsParams = {
            param($inputPath, $outputPath)
            @{
                inputPath = $inputPath
                outputPath = $outputPath
                options = @{ getLengthOnly = $false }
            }
        }
    },
    [pscustomobject]@{
        Name = "table_transpose"
        OpenToolsId = "table.transpose"
        TbtoolsMethod = "TableTools.transpose"
        Input = $inputs.Table
        TbtoolsParams = {
            param($inputPath, $outputPath)
            @{
                inputPath = $inputPath
                outputPath = $outputPath
            }
        }
    }
)

$results = @()
foreach ($workload in $workloads) {
    $tbtoolsRuns = @()
    $openPrefix = Join-Path $benchmarkRoot "$($workload.Name)-opentools"
    $openRun = Invoke-MeasuredProcess `
        -Name "$($workload.Name) OpenTools" `
        -Arguments @(
            "-cp", $jarPath,
            $harnessPath,
            $project,
            $workload.OpenToolsId,
            $workload.Input,
            $openPrefix,
            ($Iterations + 1).ToString()
        )
    $openReport = $openRun.stdout | ConvertFrom-Json
    $openTimes = @($openReport.milliseconds | ForEach-Object { [double]$_ })
    Assert-Output "$openPrefix-$Iterations.tsv" "$($workload.Name) OpenTools"

    $warmOutput = Join-Path $benchmarkRoot "$($workload.Name)-tbtools-warmup.tsv"
    $warmParams = & $workload.TbtoolsParams $workload.Input $warmOutput
    $null = Invoke-MeasuredTbtoolsRpc `
        -Name "$($workload.Name) TBtools warmup" `
        -Method $workload.TbtoolsMethod `
        -Params $warmParams `
        -ProcessId $rpcPid
    Assert-Output $warmOutput "$($workload.Name) TBtools warmup"

    for ($iteration = 0; $iteration -lt $Iterations; $iteration++) {
        $tbtoolsOutput = Join-Path $benchmarkRoot "$($workload.Name)-tbtools-$iteration.tsv"
        $tbtoolsParams = & $workload.TbtoolsParams $workload.Input $tbtoolsOutput
        $tbtoolsRun = Invoke-MeasuredTbtoolsRpc `
            -Name "$($workload.Name) TBtools" `
            -Method $workload.TbtoolsMethod `
            -Params $tbtoolsParams `
            -ProcessId $rpcPid
        Assert-Output $tbtoolsOutput "$($workload.Name) TBtools"
        $tbtoolsRuns += $tbtoolsRun
    }
    $tbtoolsTimes = @($tbtoolsRuns | ForEach-Object { $_.milliseconds })
    $results += [ordered]@{
        workload = $workload.Name
        inputBytes = (Get-Item -LiteralPath $workload.Input).Length
        openTools = [ordered]@{
            milliseconds = $openTimes
            medianMilliseconds = [Math]::Round((Get-Median $openTimes), 1)
            sampledWorkingSetMiB = $openRun.peakWorkingSetMiB
        }
        tbtools = [ordered]@{
            milliseconds = $tbtoolsTimes
            medianMilliseconds = [Math]::Round((Get-Median $tbtoolsTimes), 1)
            sampledWorkingSetMiB = @(
                $tbtoolsRuns | ForEach-Object { $_.peakWorkingSetMiB }
            )
        }
        openToolsToTbtoolsRatio = [Math]::Round(
            (Get-Median $openTimes) / (Get-Median $tbtoolsTimes),
            3
        )
    }
}

$report = [ordered]@{
    generatedAt = (Get-Date).ToString("o")
    java = (& $java -version 2>&1 | Select-Object -First 1).ToString()
    tbtoolsProcessId = $rpcPid
    iterations = $Iterations
    warmupIterations = 1
    fastaRecords = $FastaRecords
    tableRows = $TableRows
    tableColumns = 11
    note = "Task timings exclude both JVM startup and one warmup in each engine. TBtools runs in a persistent GUI process. OpenTools runs through a persistent in-process harness; process working-set values are sampled and are not directly comparable because TBtools includes the GUI baseline."
    results = $results
}
$reportPath = Join-Path $benchmarkRoot "latest.json"
$report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $reportPath -Encoding UTF8
$report | ConvertTo-Json -Depth 10
Write-Output "Report: $reportPath"

if (-not $KeepOutputs) {
    Get-ChildItem -LiteralPath $benchmarkRoot -Filter "*-opentools-*.tsv" |
        Remove-Item -Force
    Get-ChildItem -LiteralPath $benchmarkRoot -Filter "*-tbtools-*.tsv" |
        Remove-Item -Force
    Get-ChildItem -LiteralPath $benchmarkRoot -Filter "*-tbtools-warmup.tsv" |
        Remove-Item -Force
    Get-ChildItem -LiteralPath $benchmarkRoot -Filter "table-memory-*.tsv" |
        Remove-Item -Force
    Get-ChildItem -LiteralPath $benchmarkRoot -Filter "table-tbtools-*.tsv" |
        Remove-Item -Force
    Get-ChildItem -LiteralPath $benchmarkRoot -Filter "tbtools-fasta-test*.tsv" |
        Remove-Item -Force
    Get-ChildItem -LiteralPath $benchmarkRoot -Filter "gff-*-inspect*.tsv" |
        Remove-Item -Force
    Get-ChildItem -LiteralPath $benchmarkRoot -Filter "harness-test-*.tsv" |
        Remove-Item -Force
}
