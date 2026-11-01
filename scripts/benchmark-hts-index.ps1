param(
    [string]$Jar = "opentools-app\target\dist\opentools.jar",
    [int]$Records = 200000,
    [int]$Iterations = 3
)

$ErrorActionPreference = "Stop"
$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$jarPath = (Resolve-Path (Join-Path $workspace $Jar)).Path
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$benchmarkDir = Join-Path $workspace ".verification\benchmark\hts-index-$stamp"
$project = Join-Path $benchmarkDir "project"
New-Item -ItemType Directory -Force -Path $benchmarkDir, $project | Out-Null

$sam = Join-Path $project "large.sam"
$sequence = "ACGT" * 25
$quality = "I" * 100
$writer = [System.IO.StreamWriter]::new($sam, $false, [System.Text.UTF8Encoding]::new($false))
try {
    $writer.WriteLine("@HD`tVN:1.6`tSO:coordinate")
    $writer.WriteLine("@SQ`tSN:chr1`tLN:2000000")
    for ($index = 0; $index -lt $Records; $index++) {
        $position = [Math]::Max(1, [Math]::Floor($index * 1900000.0 / $Records))
        $writer.WriteLine("read$index`t0`tchr1`t$position`t60`t100M`t*`t0`t0`t$sequence`t$quality")
    }
}
finally {
    $writer.Dispose()
}

$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java.exe" }

function Invoke-OpenTools([string[]]$Arguments) {
    & $java -jar $jarPath --project $project @Arguments 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "OpenTools command failed with exit code $LASTEXITCODE"
    }
}

function Measure-OpenTools([string]$InputPath, [string]$OutputPath) {
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    Invoke-OpenTools @(
        "run", "hts.bam.subset",
        "--param", "input=$InputPath",
        "--param", "regions=chr1:1800000-1820000",
        "--param", "output=$OutputPath",
        "--param", "createIndex=false",
        "--json"
    )
    $timer.Stop()
    return $timer.Elapsed.TotalMilliseconds
}

function Measure-Coverage([string]$InputPath, [string]$OutputPath) {
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    Invoke-OpenTools @(
        "run", "hts.bam.coverage",
        "--param", "input=$InputPath",
        "--param", "regions=$coverageBed",
        "--param", "output=$OutputPath",
        "--json"
    )
    $timer.Stop()
    return $timer.Elapsed.TotalMilliseconds
}

$indexedBam = Join-Path $project "indexed.bam"
Invoke-OpenTools @(
    "run", "hts.bam.subset",
    "--param", "input=$sam",
    "--param", "regions=chr1:1-2000000",
    "--param", "output=$indexedBam",
    "--param", "createIndex=true",
    "--json"
)
$unindexedBam = Join-Path $project "unindexed.bam"
Copy-Item -LiteralPath $indexedBam -Destination $unindexedBam
$coverageBed = Join-Path $project "coverage.bed"
"chr1`t1799900`t1802100" | Set-Content -LiteralPath $coverageBed -Encoding ASCII

Invoke-OpenTools @(
    "run", "hts.bam.subset",
    "--param", "input=$indexedBam",
    "--param", "regions=chr1:1800000-1820000",
    "--param", "output=$(Join-Path $project 'warmup-indexed.bam')",
    "--param", "createIndex=false",
    "--json"
)
Invoke-OpenTools @(
    "run", "hts.bam.subset",
    "--param", "input=$unindexedBam",
    "--param", "regions=chr1:1800000-1820000",
    "--param", "output=$(Join-Path $project 'warmup-linear.bam')",
    "--param", "createIndex=false",
    "--json"
)

$indexed = @()
$linear = @()
$indexedCoverage = @()
$linearCoverage = @()
for ($index = 0; $index -lt $Iterations; $index++) {
    $indexed += Measure-OpenTools $indexedBam (Join-Path $project "indexed-$index.bam")
    $linear += Measure-OpenTools $unindexedBam (Join-Path $project "linear-$index.bam")
    $indexedCoverage += Measure-Coverage $indexedBam (Join-Path $project "indexed-$index.bedgraph")
    $linearCoverage += Measure-Coverage $unindexedBam (Join-Path $project "linear-$index.bedgraph")
}

function Get-Median([double[]]$Values) {
    $sorted = @($Values | Sort-Object)
    $middle = [Math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) {
        return $sorted[$middle]
    }
    return ($sorted[$middle - 1] + $sorted[$middle]) / 2.0
}

$indexedMedian = Get-Median $indexed
$linearMedian = Get-Median $linear
$indexedCoverageMedian = Get-Median $indexedCoverage
$linearCoverageMedian = Get-Median $linearCoverage
$javaVersion = (& $java -version 2>&1 |
    ForEach-Object { $_.ToString() } |
    Select-Object -First 1)
$result = [ordered]@{
    date = (Get-Date).ToString("o")
    java = $javaVersion
    records = $Records
    iterations = $Iterations
    region = "chr1:1800000-1820000"
    indexedMilliseconds = $indexed
    linearMilliseconds = $linear
    indexedMedianMilliseconds = [Math]::Round($indexedMedian, 2)
    linearMedianMilliseconds = [Math]::Round($linearMedian, 2)
    speedup = [Math]::Round($linearMedian / $indexedMedian, 2)
    indexedCoverageMilliseconds = $indexedCoverage
    linearCoverageMilliseconds = $linearCoverage
    indexedCoverageMedianMilliseconds = [Math]::Round($indexedCoverageMedian, 2)
    linearCoverageMedianMilliseconds = [Math]::Round($linearCoverageMedian, 2)
    coverageSpeedup = [Math]::Round($linearCoverageMedian / $indexedCoverageMedian, 2)
    note = "Single-machine process-level smoke test; CLI/JVM startup is included."
}
$report = Join-Path $benchmarkDir "hts-index-benchmark.json"
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $report -Encoding UTF8
Write-Output $report
