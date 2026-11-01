param(
    [string]$Source = "",
    [string]$Target = "C:\Program Files\OpenToolsDeps\bin",
    [switch]$Elevate
)

$ErrorActionPreference = "Stop"
$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($Source)) {
    $Source = Join-Path $workspace ".verification\OpenToolsDeps\bin"
}
$sourcePath = (Resolve-Path -LiteralPath $Source).Path
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    if ($Elevate) {
        $quote = { param($value) $value.Replace("'", "''") }
        $command = "& '$(& $quote $PSCommandPath)' -Source '$(& $quote $Source)' " +
            "-Target '$(& $quote $Target)'"
        $encoded = [Convert]::ToBase64String(
            [Text.Encoding]::Unicode.GetBytes($command)
        )
        $process = Start-Process -FilePath "pwsh.exe" -Verb RunAs -Wait -PassThru `
            -ArgumentList "-NoProfile", "-EncodedCommand", $encoded
        if ($process.ExitCode -ne 0) {
            throw "Elevated dependency installation failed with exit code $($process.ExitCode)."
        }
        exit 0
    }
    throw "This installer must run from an elevated PowerShell process."
}

New-Item -ItemType Directory -Force -Path $Target | Out-Null
Copy-Item -Path (Join-Path $sourcePath "*") -Destination $Target -Recurse -Force

$environmentKey = "HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Environment"
$currentPath = (Get-ItemProperty -LiteralPath $environmentKey -Name Path).Path
$segments = @($currentPath -split ";" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
if (-not ($segments | Where-Object { $_.TrimEnd("\") -ieq $Target.TrimEnd("\") })) {
    $newPath = (@($segments) + $Target) -join ";"
    New-ItemProperty -LiteralPath $environmentKey -Name Path -PropertyType ExpandString `
        -Value $newPath -Force | Out-Null
}

$report = [ordered]@{
    installedAt = (Get-Date).ToString("o")
    source = $sourcePath
    target = $Target
    machinePathUpdated = $true
    administrator = $identity.Name
    files = @(Get-ChildItem -LiteralPath $Target -File | ForEach-Object {
        [pscustomobject]@{
            name = $_.Name
            size = $_.Length
            sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
        }
    })
}
$reportPath = Join-Path (Split-Path -Parent $Target) "install-report.json"
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding UTF8
Write-Output $reportPath
