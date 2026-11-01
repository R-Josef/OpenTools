param(
    [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"
$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$toolNames = @(
    "samtools",
    "makeblastdb",
    "blastn",
    "blastp",
    "diamond",
    "hmmscan",
    "hmmsearch",
    "muscle",
    "trimal",
    "iqtree2",
    "iqtree",
    "MCScanX"
)
$dependencyBin = "C:\Program Files\OpenToolsDeps\bin"
$candidateDirectories = @(
    $dependencyBin,
    "C:\Program Files\NCBI\blast-2.17.0+\bin"
)

function Get-ToolState {
    param([string]$Name)
    $command = Get-Command $Name -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $command) {
        foreach ($directory in $candidateDirectories) {
            $candidate = Get-ChildItem -LiteralPath $directory -File -ErrorAction SilentlyContinue |
                Where-Object { $_.BaseName -ieq $Name } |
                Select-Object -First 1
            if ($null -ne $candidate) {
                $command = [pscustomobject]@{ Source = $candidate.FullName }
                break
            }
        }
    }
    if ($null -eq $command) {
        return [pscustomobject]@{
            name = $Name
            found = $false
            path = $null
            version = $null
        }
    }
    $version = $null
    try {
        $version = (& $command.Source --version 2>&1 |
            ForEach-Object { $_.ToString() } |
            Select-Object -First 2) -join " "
    } catch {
        $version = "version command unavailable"
    }
    return [pscustomobject]@{
        name = $Name
        found = $true
        path = $command.Source
        version = $version
    }
}

Add-Type -AssemblyName System.Windows.Forms
$screens = @([System.Windows.Forms.Screen]::AllScreens | ForEach-Object {
    [pscustomobject]@{
        deviceName = $_.DeviceName
        primary = $_.Primary
        width = $_.Bounds.Width
        height = $_.Bounds.Height
        workingWidth = $_.WorkingArea.Width
        workingHeight = $_.WorkingArea.Height
    }
})

$system = Get-CimInstance Win32_ComputerSystem
$os = Get-CimInstance Win32_OperatingSystem
$processors = @(Get-CimInstance Win32_Processor | ForEach-Object {
    [pscustomobject]@{
        name = $_.Name.Trim()
        cores = $_.NumberOfCores
        logicalProcessors = $_.NumberOfLogicalProcessors
        maxClockMHz = $_.MaxClockSpeed
    }
})
$videoControllers = @(Get-CimInstance Win32_VideoController | ForEach-Object {
    [pscustomobject]@{
        name = $_.Name
        currentWidth = $_.CurrentHorizontalResolution
        currentHeight = $_.CurrentVerticalResolution
        currentRefreshHz = $_.CurrentRefreshRate
    }
})
$disks = @(Get-CimInstance Win32_LogicalDisk -Filter "DriveType=3" | ForEach-Object {
    [pscustomobject]@{
        drive = $_.DeviceID
        sizeGiB = [Math]::Round($_.Size / 1GB, 2)
        freeGiB = [Math]::Round($_.FreeSpace / 1GB, 2)
    }
})
$environmentKey = "HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Environment"
$machinePath = (Get-ItemProperty -LiteralPath $environmentKey -Name Path).Path
$machinePathResolution = @{}
foreach ($name in @("hmmscan", "hmmsearch", "muscle", "trimal", "iqtree2", "MCScanX", "diamond")) {
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = "$env:SystemRoot\System32\where.exe"
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    $startInfo.ArgumentList.Add($name)
    $startInfo.Environment["PATH"] = $machinePath
    $process = [Diagnostics.Process]::Start($startInfo)
    $output = $process.StandardOutput.ReadToEnd().Trim()
    $process.WaitForExit()
    $machinePathResolution[$name] = if ($process.ExitCode -eq 0) {
        ($output -split "`r?`n" | Select-Object -First 1)
    } else {
        $null
    }
}

$report = [ordered]@{
    date = (Get-Date).ToString("o")
    workspace = $workspace
    os = [pscustomobject]@{
        caption = $os.Caption
        version = $os.Version
        build = $os.BuildNumber
        architecture = $os.OSArchitecture
    }
    memoryGiB = [Math]::Round($system.TotalPhysicalMemory / 1GB, 2)
    processors = $processors
    screens = $screens
    videoControllers = $videoControllers
    disks = $disks
    machinePath = $machinePath
    dependencyBin = $dependencyBin
    dependencyBinOnMachinePath = @(
        $machinePath -split ";" | Where-Object {
            $_.TrimEnd("\") -ieq $dependencyBin.TrimEnd("\")
        }
    ).Count -gt 0
    machinePathResolution = $machinePathResolution
    tools = @($toolNames | ForEach-Object { Get-ToolState $_ })
    environment = [pscustomobject]@{
        JAVA_HOME = $env:JAVA_HOME
        OPENTOOLS_HOME = $env:OPENTOOLS_HOME
        OPENTOOLS_HMMSCAN = $env:OPENTOOLS_HMMSCAN
        OPENTOOLS_HMMSEARCH = $env:OPENTOOLS_HMMSEARCH
        OPENTOOLS_MUSCLE = $env:OPENTOOLS_MUSCLE
        OPENTOOLS_IQTREE = $env:OPENTOOLS_IQTREE
        OPENTOOLS_MCSCANX = $env:OPENTOOLS_MCSCANX
    }
}

$json = $report | ConvertTo-Json -Depth 8
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    Write-Output $json
} else {
    $parent = Split-Path -Parent $OutputPath
    if (-not [string]::IsNullOrWhiteSpace($parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    [System.IO.File]::WriteAllText(
        $OutputPath,
        $json,
        [System.Text.UTF8Encoding]::new($false)
    )
    Write-Output $OutputPath
}
