param(
    [double]$Scale = 1.0,
    [string]$Jar = "opentools-app\target\dist\opentools.jar",
    [string]$HomePath = ".verification\gui-4k-home"
)

$ErrorActionPreference = "Stop"
$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$jarPath = (Resolve-Path (Join-Path $workspace $Jar)).Path
$homePath = if ([IO.Path]::IsPathRooted($HomePath)) {
    [IO.Path]::GetFullPath($HomePath)
} else {
    [IO.Path]::GetFullPath((Join-Path $workspace $HomePath))
}
New-Item -ItemType Directory -Force -Path $homePath | Out-Null
$machinePath = (Get-ItemProperty -LiteralPath `
    "HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" `
    -Name Path).Path
if (-not [string]::IsNullOrWhiteSpace($machinePath)) {
    $env:Path = "$machinePath;$env:Path"
}
$java = if ($env:JAVA_HOME) {
    Join-Path $env:JAVA_HOME "bin\java.exe"
} elseif (Test-Path -LiteralPath "C:\Program Files\Java\jdk-21\bin\java.exe") {
    "C:\Program Files\Java\jdk-21\bin\java.exe"
} else {
    (Get-Command java.exe).Source
}
$standardOut = Join-Path $homePath "gui.stdout.log"
$standardError = Join-Path $homePath "gui.stderr.log"
$process = Start-Process -FilePath $java -PassThru `
    -WorkingDirectory $workspace `
    -RedirectStandardOutput $standardOut `
    -RedirectStandardError $standardError `
    -ArgumentList @(
        "-Dopentools.ui.scale=$Scale",
        "-Dopentools.home=$homePath",
        "-Dopentools.language=zh_CN",
        "-jar",
        $jarPath
    )

[pscustomobject]@{
    pid = $process.Id
    scale = $Scale
    home = $homePath
    stdout = $standardOut
    stderr = $standardError
} | ConvertTo-Json
