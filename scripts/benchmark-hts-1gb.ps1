param(
    [string]$Jar = "opentools-app\target\opentools.jar",
    [string]$Java = "",
    [string]$StressDirectory = ".verification\stress",
    [switch]$ReuseInputs
)

$ErrorActionPreference = "Stop"
$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$jarPath = (Resolve-Path (Join-Path $workspace $Jar)).Path
$stress = [IO.Path]::GetFullPath((Join-Path $workspace $StressDirectory))
$runStamp = Get-Date -Format "yyyyMMdd-HHmmss"
$resultDirectory = Join-Path $stress "results-$runStamp"
$project = Join-Path $resultDirectory "project"
New-Item -ItemType Directory -Force -Path $stress, $resultDirectory, $project | Out-Null

$javaPath = if (-not [string]::IsNullOrWhiteSpace($Java)) {
    (Resolve-Path -LiteralPath $Java).Path
} elseif (Test-Path -LiteralPath "C:\Program Files\Java\jdk-21\bin\java.exe") {
    "C:\Program Files\Java\jdk-21\bin\java.exe"
} else {
    (Get-Command java.exe).Source
}
$javacPath = Join-Path (Split-Path -Parent $javaPath) "javac.exe"
$classDirectory = Join-Path $stress "generator-classes"
$generatorSource = Join-Path $PSScriptRoot "stress\GenerateHtsStressData.java"
New-Item -ItemType Directory -Force -Path $classDirectory | Out-Null
& $javacPath -cp $jarPath -d $classDirectory $generatorSource
if ($LASTEXITCODE -ne 0) {
    throw "Stress data generator compilation failed."
}

$bam = Join-Path $stress "bam-1g.bam"
$vcf = Join-Path $stress "vcf-1g.vcf"
$reference = Join-Path $stress "reference-100m.fa"
$pilotBam = Join-Path $stress "cram-source.bam"
$cram = Join-Path $stress "reference-dependent-v2.cram"
$generatorClassPath = "$classDirectory;$jarPath"
$gib = 1GB

if (-not $ReuseInputs -or -not (Test-Path -LiteralPath $bam) -or
        (Get-Item -LiteralPath $bam).Length -lt $gib) {
    & $javaPath -Xmx2g -cp $generatorClassPath GenerateHtsStressData `
        bam $bam $gib 1000000000
    if ($LASTEXITCODE -ne 0) {
        throw "BAM stress data generation failed."
    }
}
if (-not $ReuseInputs -or -not (Test-Path -LiteralPath $vcf) -or
        (Get-Item -LiteralPath $vcf).Length -lt $gib) {
    & $javaPath -Xmx1g -cp $generatorClassPath GenerateHtsStressData `
        vcf $vcf $gib
    if ($LASTEXITCODE -ne 0) {
        throw "VCF stress data generation failed."
    }
}
if (-not $ReuseInputs -or -not (Test-Path -LiteralPath $reference) -or
        -not (Test-Path -LiteralPath "$reference.fai") -or
        -not (Test-Path -LiteralPath ([IO.Path]::ChangeExtension($reference, ".dict")))) {
    & $javaPath -Xmx512m -cp $generatorClassPath GenerateHtsStressData `
        reference $reference 100000000
    if ($LASTEXITCODE -ne 0) {
        throw "CRAM reference generation failed."
    }
}
if (-not $ReuseInputs -or -not (Test-Path -LiteralPath $pilotBam)) {
    & $javaPath -Xmx1g -cp $generatorClassPath GenerateHtsStressData `
        bam $pilotBam 67108864 100000000
    if ($LASTEXITCODE -ne 0) {
        throw "CRAM source BAM generation failed."
    }
}
if (-not $ReuseInputs -or -not (Test-Path -LiteralPath $cram)) {
    & $javaPath -Xmx1g -cp $generatorClassPath GenerateHtsStressData `
        cram $pilotBam $reference $cram
    if ($LASTEXITCODE -ne 0) {
        throw "CRAM generation failed."
    }
}

function Get-TransientBytes {
    $total = 0L
    $runRoot = Join-Path $project "runs"
    if (Test-Path -LiteralPath $runRoot) {
        $files = Get-ChildItem -LiteralPath $runRoot -Recurse -Force -File -ErrorAction SilentlyContinue |
            Where-Object {
                $_.Name.StartsWith(".opentools-") -or
                $_.Name -like "*.sqlite*" -or
                $_.Name -like "*.bam" -or
                $_.Name -like "*.cram"
            }
        if ($files) {
            $total += [long](($files | Measure-Object -Property Length -Sum).Sum)
        }
    }
    $staged = Get-ChildItem -LiteralPath $resultDirectory -Force -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name.StartsWith(".opentools-") }
    if ($staged) {
        $total += [long](($staged | Measure-Object -Property Length -Sum).Sum)
    }
    return $total
}

function Invoke-MeasuredTask {
    param(
        [string]$Name,
        [string]$Tool,
        [System.Collections.Specialized.OrderedDictionary]$Parameters
    )
    $stdoutPath = Join-Path $resultDirectory "$Name.stdout.json"
    $stderrPath = Join-Path $resultDirectory "$Name.stderr.log"
    $arguments = @("-jar", $jarPath, "--project", $project, "run", $Tool)
    foreach ($entry in $Parameters.GetEnumerator()) {
        $arguments += @("--param", "$($entry.Key)=$($entry.Value)")
    }
    $arguments += "--json"

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $javaPath
    $startInfo.WorkingDirectory = $workspace
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    foreach ($argument in $arguments) {
        $startInfo.ArgumentList.Add($argument)
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $timer = [Diagnostics.Stopwatch]::StartNew()
    if (-not $process.Start()) {
        throw "Could not start $Name."
    }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $peakWorkingSet = 0L
    $peakTransient = 0L
    while (-not $process.HasExited) {
        try {
            $process.Refresh()
            $peakWorkingSet = [Math]::Max($peakWorkingSet, [long]$process.PeakWorkingSet64)
        } catch {
        }
        $peakTransient = [Math]::Max($peakTransient, (Get-TransientBytes))
        Start-Sleep -Milliseconds 100
    }
    $process.WaitForExit()
    $timer.Stop()
    try {
        $process.Refresh()
        $peakWorkingSet = [Math]::Max($peakWorkingSet, [long]$process.PeakWorkingSet64)
    } catch {
    }
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    [IO.File]::WriteAllText($stdoutPath, $stdout)
    [IO.File]::WriteAllText($stderrPath, $stderr)
    $exitCode = $process.ExitCode
    $process.Dispose()
    $json = $null
    try {
        $json = $stdout | ConvertFrom-Json
    } catch {
    }
    $result = [pscustomobject]@{
        name = $Name
        tool = $Tool
        exitCode = $exitCode
        ok = ($null -ne $json -and $json.ok)
        milliseconds = [Math]::Round($timer.Elapsed.TotalMilliseconds, 2)
        peakWorkingSetMiB = [Math]::Round($peakWorkingSet / 1MB, 2)
        peakTransientMiB = [Math]::Round($peakTransient / 1MB, 2)
        stdout = $stdoutPath
        stderr = $stderrPath
    }
    if ($exitCode -ne 0 -or -not $result.ok) {
        throw "$Name failed. See $stdoutPath and $stderrPath"
    }
    return $result
}

$regions = @(
    "chr1:1-100000",
    "chr1:50000001-50100000",
    "chr1:100000001-100100000",
    "chr1:150000001-150100000",
    "chr1:200000001-200100000",
    "chr1:250000001-250100000",
    "chr1:300000001-300100000",
    "chr1:350000001-350100000"
) -join "`n"
$outputs = [ordered]@{
    bamStats = Join-Path $resultDirectory "bam-stats.tsv"
    bamSubset = Join-Path $resultDirectory "bam-subset.bam"
    vcfStats = Join-Path $resultDirectory "vcf-stats.tsv"
    bcf = Join-Path $resultDirectory "variants.bcf"
    bcfStats = Join-Path $resultDirectory "bcf-stats.tsv"
    bcfFiltered = Join-Path $resultDirectory "bcf-filtered.vcf.gz"
    cramSubset = Join-Path $resultDirectory "cram-subset.bam"
}

$measurements = @()
$measurements += Invoke-MeasuredTask "bam-stats" "hts.sam-stats" ([ordered]@{
    input = $bam
    output = $outputs.bamStats
})
$measurements += Invoke-MeasuredTask "bam-subset" "hts.bam.subset" ([ordered]@{
    input = $bam
    regions = $regions
    threads = 4
    createIndex = "true"
    output = $outputs.bamSubset
})
$measurements += Invoke-MeasuredTask "vcf-stats" "hts.vcf.stats" ([ordered]@{
    input = $vcf
    threads = 4
    output = $outputs.vcfStats
})
$measurements += Invoke-MeasuredTask "vcf-to-bcf" "hts.vcf.filter" ([ordered]@{
    input = $vcf
    threads = 4
    maxMissing = 1.0
    indexType = "CSI"
    output = $outputs.bcf
})
$measurements += Invoke-MeasuredTask "bcf-stats" "hts.vcf.stats" ([ordered]@{
    input = $outputs.bcf
    threads = 4
    output = $outputs.bcfStats
})
$measurements += Invoke-MeasuredTask "bcf-filter" "hts.vcf.filter" ([ordered]@{
    input = $outputs.bcf
    threads = 4
    maxMissing = 1.0
    indexType = "TBI"
    output = $outputs.bcfFiltered
})
$measurements += Invoke-MeasuredTask "cram-subset" "hts.bam.subset" ([ordered]@{
    input = $cram
    reference = $reference
    regions = "chr1:1-1000000`nchr1:5000001-6000000"
    threads = 2
    createIndex = "true"
    output = $outputs.cramSubset
})

$inputFiles = @($bam, $vcf, $outputs.bcf, $reference, $cram) | ForEach-Object {
    $item = Get-Item -LiteralPath $_
    [pscustomobject]@{
        path = $item.FullName
        bytes = $item.Length
        GiB = [Math]::Round($item.Length / 1GB, 3)
        sha256 = (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash
    }
}
$report = [ordered]@{
    date = (Get-Date).ToString("o")
    java = (& $javaPath -version 2>&1 | Select-Object -First 1).ToString()
    note = "Process-level OpenTools CLI run; PeakWorkingSet64 sampled every 100 ms."
    inputs = $inputFiles
    results = $measurements
}
$reportPath = Join-Path $resultDirectory "hts-1gb-report.json"
$report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $reportPath -Encoding UTF8
Write-Output $reportPath
