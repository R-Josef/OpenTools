param(
    [string]$DeviceName = "\\.\DISPLAY1",
    [int]$SetWidth = 0,
    [int]$SetHeight = 0,
    [int]$SetRefreshHz = 0,
    [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public static class DisplayModeApi {
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct DEVMODE {
        private const int CCHDEVICENAME = 32;
        private const int CCHFORMNAME = 32;

        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = CCHDEVICENAME)]
        public string dmDeviceName;
        public short dmSpecVersion;
        public short dmDriverVersion;
        public short dmSize;
        public short dmDriverExtra;
        public int dmFields;
        public int dmPositionX;
        public int dmPositionY;
        public int dmDisplayOrientation;
        public int dmDisplayFixedOutput;
        public short dmColor;
        public short dmDuplex;
        public short dmYResolution;
        public short dmTTOption;
        public short dmCollate;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = CCHFORMNAME)]
        public string dmFormName;
        public short dmLogPixels;
        public int dmBitsPerPel;
        public int dmPelsWidth;
        public int dmPelsHeight;
        public int dmDisplayFlags;
        public int dmDisplayFrequency;
        public int dmICMMethod;
        public int dmICMIntent;
        public int dmMediaType;
        public int dmDitherType;
        public int dmReserved1;
        public int dmReserved2;
        public int dmPanningWidth;
        public int dmPanningHeight;
    }

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern bool EnumDisplaySettings(
        string deviceName, int modeNum, ref DEVMODE devMode);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern int ChangeDisplaySettings(
        ref DEVMODE devMode, int flags);
}
"@

function Get-CurrentMode {
    $mode = [DisplayModeApi+DEVMODE]::new()
    $mode.dmSize = [Runtime.InteropServices.Marshal]::SizeOf($mode)
    if (-not [DisplayModeApi]::EnumDisplaySettings($DeviceName, -1, [ref]$mode)) {
        throw "Could not read current display mode for $DeviceName."
    }
    return [pscustomobject]@{
        width = $mode.dmPelsWidth
        height = $mode.dmPelsHeight
        refreshHz = $mode.dmDisplayFrequency
        bitsPerPixel = $mode.dmBitsPerPel
    }
}

function Get-Modes {
    $modes = @()
    for ($index = 0; $index -lt 4096; $index++) {
        $mode = [DisplayModeApi+DEVMODE]::new()
        $mode.dmSize = [Runtime.InteropServices.Marshal]::SizeOf($mode)
        if (-not [DisplayModeApi]::EnumDisplaySettings($DeviceName, $index, [ref]$mode)) {
            break
        }
        $modes += [pscustomobject]@{
            width = $mode.dmPelsWidth
            height = $mode.dmPelsHeight
            refreshHz = $mode.dmDisplayFrequency
            bitsPerPixel = $mode.dmBitsPerPel
        }
    }
    return @($modes | Sort-Object width, height, refreshHz -Unique)
}

$before = Get-CurrentMode
$modes = Get-Modes
$change = $null
if ($SetWidth -gt 0 -and $SetHeight -gt 0) {
    $candidate = $modes |
        Where-Object {
            $_.width -eq $SetWidth -and
            $_.height -eq $SetHeight -and
            ($SetRefreshHz -le 0 -or $_.refreshHz -eq $SetRefreshHz)
        } |
        Sort-Object refreshHz -Descending |
        Select-Object -First 1
    if ($null -eq $candidate) {
        throw "Requested display mode $SetWidth x $SetHeight is not advertised."
    }
    $mode = [DisplayModeApi+DEVMODE]::new()
    $mode.dmSize = [Runtime.InteropServices.Marshal]::SizeOf($mode)
    $mode.dmPelsWidth = $candidate.width
    $mode.dmPelsHeight = $candidate.height
    $mode.dmDisplayFrequency = $candidate.refreshHz
    $mode.dmBitsPerPel = $candidate.bitsPerPixel
    $mode.dmFields = 0x00080000 -bor 0x00100000 -bor 0x00400000 -bor 0x00040000
    $result = [DisplayModeApi]::ChangeDisplaySettings([ref]$mode, 0)
    if ($result -ne 0) {
        throw "ChangeDisplaySettings failed with code $result."
    }
    $change = $candidate
}

$report = [pscustomobject]@{
    deviceName = $DeviceName
    before = $before
    after = Get-CurrentMode
    changedTo = $change
    modes = $modes
}
$json = $report | ConvertTo-Json -Depth 6
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    Write-Output $json
} else {
    $parent = Split-Path -Parent $OutputPath
    if (-not [string]::IsNullOrWhiteSpace($parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    [IO.File]::WriteAllText($OutputPath, $json, [Text.UTF8Encoding]::new($false))
    Write-Output $OutputPath
}
