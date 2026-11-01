param(
    [string]$Jar = "opentools-app\target\dist\opentools.jar",
    [int]$TableRows = 100000,
    [int]$Variants = 300000,
    [int]$Reads = 300000,
    [int]$Iterations = 3,
    [string]$Java = ""
)

$ErrorActionPreference = "Stop"
$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$jarPath = (Resolve-Path (Join-Path $workspace $Jar)).Path
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$benchmarkDir = Join-Path $workspace ".verification\benchmark\parallel-followup-$stamp"
$project = Join-Path $benchmarkDir "project"
New-Item -ItemType Directory -Force -Path $benchmarkDir, $project | Out-Null

$java = if (-not [string]::IsNullOrWhiteSpace($Java)) {
    (Resolve-Path -LiteralPath $Java).Path
} elseif ($env:JAVA_HOME) {
    Join-Path $env:JAVA_HOME "bin\java.exe"
} else {
    (Get-Command java.exe).Source
}

function Invoke-MeasuredOpenTools([string[]]$Arguments, [string]$StdoutPath, [string]$StderrPath) {
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $java
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    foreach ($argument in @("-jar", $jarPath, "--project", $project) + $Arguments) {
        $startInfo.ArgumentList.Add($argument)
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    if (-not $process.Start()) {
        throw "Could not start OpenTools"
    }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $peak = 0L
    while (-not $process.HasExited) {
        try {
            $process.Refresh()
            $peak = [Math]::Max($peak, [long]$process.PeakWorkingSet64)
        } catch {
            # Process metrics may briefly be unavailable during exit.
        }
        Start-Sleep -Milliseconds 50
    }
    $process.WaitForExit()
    $timer.Stop()
    try {
        $process.Refresh()
        $peak = [Math]::Max($peak, [long]$process.PeakWorkingSet64)
    } catch {
        # Keep the last observed value.
    }
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    $exitCode = $process.ExitCode
    $process.Dispose()
    [System.IO.File]::WriteAllText($StdoutPath, $stdout)
    [System.IO.File]::WriteAllText($StderrPath, $stderr)
    if ($exitCode -ne 0) {
        throw "OpenTools exited with $exitCode. See $StdoutPath and $StderrPath"
    }
    return [pscustomobject]@{
        Milliseconds = [Math]::Round($timer.Elapsed.TotalMilliseconds, 2)
        PeakWorkingSetMiB = [Math]::Round($peak / 1MB, 2)
        Json = $stdout
    }
}

function Get-Median([double[]]$Values) {
    $sorted = @($Values | Sort-Object)
    $middle = [Math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) {
        return $sorted[$middle]
    }
    return ($sorted[$middle - 1] + $sorted[$middle]) / 2.0
}

function Measure-Task(
    [string]$Name,
    [string]$Tool,
    [string[]]$Parameters,
    [string]$Extension
) {
    $warmupOutput = Join-Path $project "$Name-warmup$Extension"
    $warmupArgs = @("run", $Tool, "--param", "threads=1", "--param", "output=$warmupOutput", "--json")
    foreach ($parameter in $Parameters) {
        $warmupArgs += @("--param", $parameter)
    }
    Invoke-MeasuredOpenTools $warmupArgs `
        (Join-Path $benchmarkDir "$Name-warmup.stdout") `
        (Join-Path $benchmarkDir "$Name-warmup.stderr") | Out-Null

    $measurements = @()
    foreach ($threads in @(1, 4, 0)) {
        $runs = @()
        $peakValues = @()
        $resolvedThreadValues = @()
        for ($index = 0; $index -lt $Iterations; $index++) {
            $output = Join-Path $project "$Name-t$threads-$index$Extension"
            $arguments = @("run", $Tool, "--param", "threads=$threads",
                "--param", "output=$output", "--json")
            foreach ($parameter in $Parameters) {
                $arguments += @("--param", $parameter)
            }
            $measured = Invoke-MeasuredOpenTools $arguments `
                (Join-Path $benchmarkDir "$Name-t$threads-$index.stdout") `
                (Join-Path $benchmarkDir "$Name-t$threads-$index.stderr")
            $runs += $measured.Milliseconds
            $peakValues += $measured.PeakWorkingSetMiB
            try {
                $manifest = ($measured.Json | ConvertFrom-Json).data.manifestJson |
                    ConvertFrom-Json
                $resolvedThreadValues += $manifest.result.metrics.threads
            } catch {
                $resolvedThreadValues += "unavailable"
            }
        }
        $lastOutput = Join-Path $project "$Name-t$threads-$($Iterations - 1)$Extension"
        $measurements += [pscustomobject]@{
            threads = $threads
            milliseconds = $runs
            medianMilliseconds = [Math]::Round((Get-Median $runs), 2)
            medianPeakWorkingSetMiB = [Math]::Round((Get-Median $peakValues), 2)
            maxPeakWorkingSetMiB = [Math]::Round(($peakValues | Measure-Object -Maximum).Maximum, 2)
            resolvedThreads = $resolvedThreadValues
            sha256 = (Get-FileHash -LiteralPath $lastOutput -Algorithm SHA256).Hash
        }
    }
    $sequential = $measurements[0].medianMilliseconds
    $parallel = $measurements[1].medianMilliseconds
    $automatic = $measurements[2].medianMilliseconds
    return [pscustomobject]@{
        task = $Tool
        speedup4Versus1 = [Math]::Round($sequential / $parallel, 3)
        speedupAutoVersus1 = [Math]::Round($sequential / $automatic, 3)
        results = $measurements
    }
}

function Measure-SequentialTask(
    [string]$Name,
    [string]$Tool,
    [string[]]$Parameters,
    [string]$Extension
) {
    $runs = @()
    $peakValues = @()
    for ($index = 0; $index -lt $Iterations; $index++) {
        $output = Join-Path $project "$Name-$index$Extension"
        $arguments = @("run", $Tool, "--param", "output=$output", "--json")
        foreach ($parameter in $Parameters) {
            $arguments += @("--param", $parameter)
        }
        $measured = Invoke-MeasuredOpenTools $arguments `
            (Join-Path $benchmarkDir "$Name-$index.stdout") `
            (Join-Path $benchmarkDir "$Name-$index.stderr")
        $runs += $measured.Milliseconds
        $peakValues += $measured.PeakWorkingSetMiB
    }
    $lastOutput = Join-Path $project "$Name-$($Iterations - 1)$Extension"
    return [pscustomobject]@{
        task = $Tool
        milliseconds = $runs
        medianMilliseconds = [Math]::Round((Get-Median $runs), 2)
        medianPeakWorkingSetMiB = [Math]::Round(
            (Get-Median $peakValues),
            2
        )
        maxPeakWorkingSetMiB = [Math]::Round(
            ($peakValues | Measure-Object -Maximum).Maximum,
            2
        )
        sha256 = (Get-FileHash -LiteralPath $lastOutput -Algorithm SHA256).Hash
    }
}

$table = Join-Path $project "table-100k.tsv"
$tableWriter = [System.IO.StreamWriter]::new(
    $table,
    $false,
    [System.Text.UTF8Encoding]::new($false)
)
try {
    $tableWriter.WriteLine(
        "id`tgroup`tvalue`tscore`tlabel`tleft`tright`tcenter`tstatus`tnote`textra"
    )
    for ($index = 0; $index -lt $TableRows; $index++) {
        $tableWriter.WriteLine(
            "row_$index`tgroup_$($index % 100)`t$($index % 10000)`t$($index % 997).$($index % 10)" +
            "`tlabel_$($index % 50)`t$($index % 31)`t$($index % 37)`t$($index % 41)" +
            "`tactive`t""note,`t$index""`tpayload_$($index % 113)"
        )
    }
}
finally {
    $tableWriter.Dispose()
}

$tableCases = @(
    @{
        Name = "table-filter"
        Tool = "table.filter"
        Parameters = @(
            "input=$table",
            "column=group",
            "regex=^group_[0-4][0-9]$",
            "numericColumn=value",
            "numericMin=0"
        )
    },
    @{
        Name = "table-select"
        Tool = "table.select-columns"
        Parameters = @("input=$table", "columns=note,id,group,value,score")
    },
    @{
        Name = "table-replace"
        Tool = "table.replace"
        Parameters = @("input=$table", "column=id", "search=^row_", "replacement=sample_")
    }
)
$tableResults = @()
foreach ($case in $tableCases) {
    $tableResults += Measure-Task $case.Name $case.Tool $case.Parameters ".tsv"
}

$vcf = Join-Path $project "variants-300k.vcf"
$vcfWriter = [System.IO.StreamWriter]::new(
    $vcf,
    $false,
    [System.Text.UTF8Encoding]::new($false)
)
try {
    $vcfWriter.WriteLine("##fileformat=VCFv4.2")
    $vcfWriter.WriteLine("##contig=<ID=chr1,length=1000000>")
    $vcfWriter.WriteLine('##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">')
    $vcfWriter.WriteLine(
        "#CHROM`tPOS`tID`tREF`tALT`tQUAL`tFILTER`tINFO`tFORMAT`tS1`tS2"
    )
    for ($index = 0; $index -lt $Variants; $index++) {
        $position = $index + 1
        $ref = if ($index % 7 -eq 0) { "AT" } else { "A" }
        $alt = if ($index % 7 -eq 0) { "A" } else { "G" }
        $quality = if ($index % 5 -eq 0) { 10 } else { 50 }
        $gt1 = if ($index % 11 -eq 0) { "./." } else { "0/1" }
        $gt2 = if ($index % 13 -eq 0) { "./." } else { "0/0" }
        $vcfWriter.WriteLine(
            "chr1`t$position`t.`t$ref`t$alt`t$quality`tPASS`t.`tGT`t$gt1`t$gt2"
        )
    }
}
finally {
    $vcfWriter.Dispose()
}
$vcfResult = Measure-Task "vcf-filter" "hts.vcf.filter" @(
    "input=$vcf",
    "minQual=20",
    "maxMissing=0.5",
    "indexType=AUTO"
) ".vcf.gz"

$sam = Join-Path $project "reads-300k.sam"
$sequence = "ACGT" * 25
$quality = "I" * 100
$samWriter = [System.IO.StreamWriter]::new(
    $sam,
    $false,
    [System.Text.UTF8Encoding]::new($false)
)
try {
    $samWriter.WriteLine("@HD`tVN:1.6`tSO:coordinate")
    $samWriter.WriteLine("@SQ`tSN:chr1`tLN:2000000")
    for ($index = 0; $index -lt $Reads; $index++) {
        $position = [Math]::Max(1, [Math]::Floor($index * 1900000.0 / $Reads))
        $samWriter.WriteLine(
            "read$index`t0`tchr1`t$position`t60`t100M`t*`t0`t0`t$sequence`t$quality"
        )
    }
}
finally {
    $samWriter.Dispose()
}
$indexedBam = Join-Path $project "indexed.bam"
Invoke-MeasuredOpenTools @(
    "run", "hts.bam.subset",
    "--param", "input=$sam",
    "--param", "regions=chr1:1-2000000",
    "--param", "threads=1",
    "--param", "output=$indexedBam",
    "--param", "createIndex=true",
    "--json"
) (Join-Path $benchmarkDir "bam-create.stdout") (Join-Path $benchmarkDir "bam-create.stderr") | Out-Null
$regions = @()
for ($index = 0; $index -lt 16; $index++) {
    $start = $index * 120000 + 1
    $regions += "chr1:$start-$($start + 110000)"
}
$bamResult = Measure-Task "bam-subset" "hts.bam.subset" @(
    "input=$indexedBam",
    "regions=$($regions -join "`n")",
    "createIndex=true"
) ".bam"

$gff = Join-Path $project "sparse-100k.gff3"
$gffWriter = [System.IO.StreamWriter]::new(
    $gff,
    $false,
    [System.Text.UTF8Encoding]::new($false)
)
try {
    $gffWriter.WriteLine("##gff-version 3")
    for ($index = 0; $index -lt 100000; $index++) {
        $start = $index * 10 + 1
        $gffWriter.WriteLine(
            "chr1`ttest`texon`t$start`t$($start + 4)`t.`t+`t.`tID=f$index"
        )
    }
}
finally {
    $gffWriter.Dispose()
}
$gxfMeasured = Measure-SequentialTask "gxf-overlap" "gxf.overlap" @(
    "input=$gff",
    "overlapMode=DISK"
) ".tsv"

$javaVersion = (& $java -version 2>&1 |
    ForEach-Object { $_.ToString() } |
    Select-Object -First 1)
$report = [ordered]@{
    date = (Get-Date).ToString("o")
    java = $javaVersion
    iterations = $Iterations
    table = $tableResults
    vcfFilter = $vcfResult
    bamSubset = $bamResult
    gxfOverlapSequential = $gxfMeasured
    note = "Process-level smoke test including JVM and CLI startup; the script samples PeakWorkingSet64 while each task runs."
}
$reportPath = Join-Path $benchmarkDir "parallel-followup.json"
$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath -Encoding UTF8
Write-Output $reportPath
