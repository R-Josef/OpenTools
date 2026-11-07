param(
    [string]$PackageZip = "",
    [string]$OutputDirectory = ""
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if (-not $PackageZip) {
    $PackageZip = Get-ChildItem (Join-Path $projectRoot "target\release") `
        -Filter "opentools-*-windows-x64.zip" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $PackageZip -or -not (Test-Path -LiteralPath $PackageZip -PathType Leaf)) {
    throw "Portable ZIP was not found."
}
$zipPath = (Resolve-Path -LiteralPath $PackageZip).Path
$destination = if ($OutputDirectory) {
    [IO.Path]::GetFullPath($OutputDirectory)
} else {
    Join-Path $projectRoot (".verification\portable-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
}
if (Test-Path -LiteralPath $destination) {
    throw "Verification output already exists: $destination"
}
New-Item -ItemType Directory -Path $destination | Out-Null
Expand-Archive -LiteralPath $zipPath -DestinationPath $destination
$packageRoot = Join-Path $destination "OpenTools"
$gui = Join-Path $packageRoot "OpenTools.exe"
$cli = Join-Path $packageRoot "OpenToolsCLI.exe"
if (-not (Test-Path -LiteralPath $gui) -or -not (Test-Path -LiteralPath $cli)) {
    throw "Portable launchers are missing."
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarPath = Join-Path $packageRoot "app\opentools.jar"
$jar = [IO.Compression.ZipFile]::OpenRead($jarPath)
try {
    $jarEntries = @($jar.Entries.FullName)
    foreach ($entry in @(
        "docs/README.md",
        "docs/en_US/README.md",
        "docs/en_US/cli.md",
        "docs/en_US/categories/sequence.md",
        "docs/en_US/assets/screenshots/docs-small-window.png",
        "docs/en_US/assets/screenshots/docs-home.png",
        "docs/zh_CN/README.md",
        "docs/zh_CN/cli.md",
        "docs/zh_CN/categories/sequence.md",
        "docs/zh_CN/assets/screenshots/docs-home.png"
    )) {
        if ($entry -notin $jarEntries) {
            throw "Application JAR is missing bundled Docs resource: $entry"
        }
    }
} finally {
    $jar.Dispose()
}

$packageArchive = [IO.Compression.ZipFile]::OpenRead($zipPath)
try {
    $packageEntries = @($packageArchive.Entries.FullName)
    foreach ($entry in @(
        "OpenTools/licenses/SOURCE_INFO.md",
        "OpenTools/licenses/SOURCE_BUNDLE.txt",
        "OpenTools/licenses/sources.json",
        "OpenTools/licenses/legal/LGPL-3.0.txt"
    )) {
        if ($entry -notin $packageEntries) {
            throw "Portable package is missing source/license material: $entry"
        }
    }
    if ($packageEntries | Where-Object { $_ -like "OpenTools/licenses/sources/*" }) {
        throw "Portable package must not duplicate upstream source archives."
    }
    if ($packageEntries | Where-Object { $_ -like "OpenTools/licenses/*vc_redist*" }) {
        throw "Portable package must not bundle the VC++ Redistributable installer."
    }
    if ($packageEntries | Where-Object { $_ -like "OpenTools/tools/*MSVCP140.dll" }) {
        throw "Portable tools must use the system VC++ runtime instead of local Microsoft DLLs."
    }
} finally {
    $packageArchive.Dispose()
}

$userHome = Join-Path $destination "user-home"
$project = Join-Path $destination "project"
New-Item -ItemType Directory -Force -Path $userHome, $project | Out-Null
$environment = @{
    "SystemRoot" = $env:SystemRoot
    "WINDIR" = $env:WINDIR
    "PATH" = "$env:SystemRoot\System32;$env:SystemRoot"
    "TEMP" = $env:TEMP
    "TMP" = $env:TMP
    "USERPROFILE" = $userHome
    "LOCALAPPDATA" = $userHome
    "APPDATA" = $userHome
}

function Invoke-PortableCli {
    param(
        [Parameter(Mandatory)] [string[]]$Arguments,
        [int]$TimeoutMilliseconds = 300000
    )
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $cli
    $info.WorkingDirectory = $destination
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = [Text.Encoding]::UTF8
    $info.StandardErrorEncoding = [Text.Encoding]::UTF8
    $info.Environment.Clear()
    foreach ($entry in $environment.GetEnumerator()) {
        $info.Environment[$entry.Key] = $entry.Value
    }
    foreach ($argument in $Arguments) {
        $info.ArgumentList.Add($argument)
    }
    $process = [Diagnostics.Process]::Start($info)
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    if (-not $process.WaitForExit($TimeoutMilliseconds)) {
        $process.Kill($true)
        throw "Portable CLI timed out: $($Arguments -join ' ')"
    }
    if ($process.ExitCode -ne 0) {
        throw "Portable CLI failed ($($process.ExitCode)): $($Arguments -join ' ')`n$stdout`n$stderr"
    }
    return [pscustomobject]@{
        ExitCode = $process.ExitCode
        Stdout = $stdout
        Stderr = $stderr
    }
}

function Invoke-Tool {
    param(
        [Parameter(Mandatory)] [string]$Tool,
        [Parameter(Mandatory)] [hashtable]$Parameters,
        [Parameter(Mandatory)] [string[]]$Outputs
    )
    $arguments = @("--project", $project, "run", $Tool, "--json")
    foreach ($entry in $Parameters.GetEnumerator() | Sort-Object Key) {
        $arguments += "--param"
        $arguments += "$($entry.Key)=$($entry.Value)"
    }
    $result = Invoke-PortableCli -Arguments $arguments
    $state = $result.Stdout | ConvertFrom-Json
    if (-not $state.ok -or $state.data.status -ne "SUCCEEDED") {
        throw "Tool did not succeed: $Tool`n$($result.Stdout)"
    }
    foreach ($output in $Outputs) {
        if (-not (Test-Path -LiteralPath $output -PathType Leaf) -or (Get-Item -LiteralPath $output).Length -eq 0) {
            throw "Expected non-empty output is missing for ${Tool}: $output"
        }
    }
    return $state.data
}

$tools = Invoke-PortableCli -Arguments @("tools", "list", "--json")
$toolsJson = $tools.Stdout | ConvertFrom-Json
if (-not $toolsJson.ok -or $toolsJson.data.Count -ne 93) {
    throw "Portable package did not expose 93 built-in tasks."
}

$doctor = Invoke-PortableCli -Arguments @("doctor", "--json")
$doctorJson = $doctor.Stdout | ConvertFrom-Json
if ($doctorJson.data.tasks -ne 93) {
    throw "Doctor reported an unexpected task count."
}
$optionalTools = @("annotation.meme", "annotation.mast")
foreach ($tool in $doctorJson.data.externalTools) {
    if (-not $tool.available) {
        if ($optionalTools -contains $tool.id) {
            continue
        }
        throw "External tool is unavailable in the portable package: $($tool.id)"
    }
    $parts = [regex]::Split(
        [string]$tool.executable,
        [regex]::Escape("\OpenTools\tools\"),
        [Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    if ($parts.Count -ne 2) {
        throw "External tool did not resolve through the package tools directory: $($tool.executable)"
    }
    $resolvedExecutable = Join-Path (Join-Path $packageRoot "tools") $parts[1]
    if (-not (Test-Path -LiteralPath $resolvedExecutable -PathType Leaf)) {
        throw "External tool path does not exist in the extracted package: $resolvedExecutable"
    }
}
foreach ($id in @("blast.blastn", "blast.blastp", "blast.makeblastdb",
        "diamond", "hmmer.hmmscan", "hmmer.hmmsearch", "alignment.muscle",
        "alignment.trimal", "alignment.iqtree", "comparative.mcscanx")) {
    $tool = $doctorJson.data.externalTools | Where-Object id -eq $id
    if ($null -eq $tool) {
        throw "Doctor did not report external tool: $id"
    }
}

$out = Join-Path $destination "outputs"
$fixtures = Join-Path $destination "fixtures"
New-Item -ItemType Directory -Force -Path $out, $fixtures | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot "examples\external-tools\protein-unaligned.fa") -Destination $fixtures
Copy-Item -LiteralPath (Join-Path $projectRoot "examples\sequence\sample.fa") -Destination $fixtures
Copy-Item -LiteralPath (Join-Path $projectRoot ".verification\tool-e2e\model.hmm") -Destination $fixtures
foreach ($suffix in @(".h3f", ".h3i", ".h3m", ".h3p")) {
    Copy-Item -LiteralPath (Join-Path $projectRoot ".verification\tool-e2e\model.hmm$suffix") -Destination $fixtures
}
$mcscanPrefix = Join-Path $fixtures "mcscan"
Copy-Item -LiteralPath (Join-Path $projectRoot ".verification\tool-e2e\mcscan-fixed.blast") `
    -Destination "$mcscanPrefix.blast"
Copy-Item -LiteralPath (Join-Path $projectRoot ".verification\tool-e2e\mcscan-fixed.gff") `
    -Destination "$mcscanPrefix.gff"

$protein = Join-Path $fixtures "protein-unaligned.fa"
$model = Join-Path $fixtures "model.hmm"
$results = [ordered]@{}
$results["fasta.stats"] = Invoke-Tool -Tool "fasta.stats" -Parameters @{
    input = Join-Path $fixtures "sample.fa"
    output = Join-Path $out "fasta-stats.tsv"
} -Outputs @(Join-Path $out "fasta-stats.tsv")

$blastOutput = Join-Path $out "blast.tsv"
$results["alignment.blast.local"] = Invoke-Tool -Tool "alignment.blast.local" -Parameters @{
    query = $protein
    subject = $protein
    program = "BLASTP"
    output = $blastOutput
} -Outputs @($blastOutput)

$diamondOutput = Join-Path $out "diamond.tsv"
$results["alignment.diamond"] = Invoke-Tool -Tool "alignment.diamond" -Parameters @{
    query = $protein
    subject = $protein
    output = $diamondOutput
} -Outputs @($diamondOutput)

$aligned = Join-Path $out "aligned.fa"
$results["alignment.muscle"] = Invoke-Tool -Tool "alignment.muscle" -Parameters @{
    input = $protein
    output = $aligned
    threads = 2
} -Outputs @($aligned)

$trimmed = Join-Path $out "trimmed.fa"
$results["alignment.trim"] = Invoke-Tool -Tool "alignment.trim" -Parameters @{
    input = $aligned
    output = $trimmed
} -Outputs @($trimmed)

$tree = Join-Path $out "iqtree.treefile"
$results["alignment.iqtree"] = Invoke-Tool -Tool "alignment.iqtree" -Parameters @{
    input = $trimmed
    output = $tree
    model = "LG"
    bootstrap = "false"
    threads = 1
} -Outputs @($tree)

$hmmsearch = Join-Path $out "hmmsearch.domtbl"
$results["alignment.hmmer.hmmsearch"] = Invoke-Tool -Tool "alignment.hmmer.hmmsearch" -Parameters @{
    hmm = $model
    sequences = $protein
    domtbl = $hmmsearch
} -Outputs @($hmmsearch)

$hmmscan = Join-Path $out "hmmscan.domtbl"
$results["alignment.hmmer.search"] = Invoke-Tool -Tool "alignment.hmmer.search" -Parameters @{
    database = $model
    sequences = $protein
    domtbl = $hmmscan
} -Outputs @($hmmscan)

$collinearity = Join-Path $out "mcscan.collinearity"
$results["mcscan.run"] = Invoke-Tool -Tool "mcscan.run" -Parameters @{
    prefix = $mcscanPrefix
    output = $collinearity
} -Outputs @($collinearity)

$report = [ordered]@{
    package = $zipPath
    packageSha256 = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash
    extractedTo = $packageRoot
    cleanPath = $environment.PATH
    javaHomeRemoved = $true
    tasks = $toolsJson.data.Count
    externalTools = $doctorJson.data.externalTools
    toolRuns = $results
    verifiedAt = [DateTimeOffset]::UtcNow.ToString("o")
}
$reportPath = Join-Path $destination "verification.json"
$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath -Encoding utf8
Write-Output $reportPath
