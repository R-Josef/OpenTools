param(
    [string]$CacheDirectory = "",
    [string]$NativeBuildDirectory = "",
    [string]$MemeDirectory = "",
    [string]$OutputDirectory = "",
    [switch]$Offline,
    [switch]$SkipBuild,
    [switch]$KeepWork
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$manifestPath = Join-Path $projectRoot "packaging\windows\tools.json"
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
if ($manifest.release -ne "windows-x64" -or $manifest.platform -ne "windows" -or $manifest.architecture -ne "x64") {
    throw "The tool manifest is not a Windows x64 release manifest."
}
if (-not [Environment]::Is64BitOperatingSystem -or $env:PROCESSOR_ARCHITECTURE -notmatch "AMD64|x86") {
    throw "This release builder currently supports Windows x64 only."
}

$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Java\jdk-21" }
$jpackage = Join-Path $javaHome "bin\jpackage.exe"
$java = Join-Path $javaHome "bin\java.exe"
if (-not (Test-Path -LiteralPath $jpackage -PathType Leaf)) {
    throw "jpackage was not found under JAVA_HOME: $jpackage"
}
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
    throw "java was not found under JAVA_HOME: $java"
}
$javaVersion = & $java -version 2>&1 | Select-Object -First 1
if ($javaVersion -notmatch '"21(\.|")') {
    throw "Windows packaging requires JDK 21. Found: $javaVersion"
}

$cache = if ($CacheDirectory) {
    [IO.Path]::GetFullPath($CacheDirectory)
} else {
    Join-Path $projectRoot ".release-cache\windows"
}
New-Item -ItemType Directory -Force -Path $cache | Out-Null
$output = if ($OutputDirectory) {
    [IO.Path]::GetFullPath($OutputDirectory)
} else {
    Join-Path $projectRoot "target\release"
}
New-Item -ItemType Directory -Force -Path $output | Out-Null

$pom = [xml](Get-Content -Raw -LiteralPath (Join-Path $projectRoot "pom.xml"))
$version = [string]$pom.project.version
if ([string]::IsNullOrWhiteSpace($version)) {
    throw "Could not read the project version."
}
$appVersion = ($version -split "-")[0]
$packageName = "opentools-$version-windows-x64"
$work = Join-Path $projectRoot "target\release-work"

function Reset-WorkspaceDirectory {
    param([string]$Path)
    $full = [IO.Path]::GetFullPath($Path).TrimEnd("\")
    $root = [IO.Path]::GetFullPath($projectRoot).TrimEnd("\")
    if (-not $full.StartsWith($root + "\", [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clear directory outside the project workspace: $full"
    }
    if (Test-Path -LiteralPath $full) {
        Remove-Item -LiteralPath $full -Recurse -Force
    }
    New-Item -ItemType Directory -Path $full | Out-Null
}

function Get-VerifiedArtifact {
    param(
        [Parameter(Mandatory)] [object]$Artifact,
        [Parameter(Mandatory)] [string]$Label
    )
    $name = [string]$Artifact.file
    if ([string]::IsNullOrWhiteSpace($name) -or $name -match '[\\/]') {
        throw "Unsafe artifact file name for ${Label}: $name"
    }
    $path = Join-Path $cache $name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        if ($Offline) {
            throw "Offline build is missing $Label artifact: $name"
        }
        $url = [string]$Artifact.url
        if ($url -notmatch '^https://') {
            throw "Artifact URL must use HTTPS for ${Label}: $url"
        }
        $temporary = "$path.download"
        if (Test-Path -LiteralPath $temporary) {
            Remove-Item -LiteralPath $temporary -Force
        }
        & curl.exe -L --fail --show-error --retry 4 --retry-delay 2 --output $temporary $url
        if ($LASTEXITCODE -ne 0) {
            throw "Download failed for $Label ($url)."
        }
        Move-Item -LiteralPath $temporary -Destination $path
    }
    $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
    $expected = ([string]$Artifact.sha256).ToUpperInvariant()
    if ($actual -ne $expected) {
        throw "SHA-256 mismatch for $Label. Expected $expected, found $actual. Refusing to package."
    }
    return $path
}

function Expand-Artifact {
    param(
        [Parameter(Mandatory)] [string]$Path,
        [Parameter(Mandatory)] [string]$Destination,
        [Parameter(Mandatory)] [string]$Type
    )
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    switch ($Type) {
        "zip" {
            Expand-Archive -LiteralPath $Path -DestinationPath $Destination
        }
        "tar.gz" {
            & tar.exe -xf $Path -C $Destination
            if ($LASTEXITCODE -ne 0) {
                throw "Could not extract tar archive: $Path"
            }
        }
        default {
            throw "Unsupported archive type: $Type"
        }
    }
}

function Copy-ToolPattern {
    param(
        [Parameter(Mandatory)] [string]$SourceRoot,
        [Parameter(Mandatory)] [string]$DestinationRoot,
        [Parameter(Mandatory)] [string]$PatternSpec
    )
    $parts = $PatternSpec.Split("|", 2)
    $pattern = $parts[0]
    $explicitDestination = if ($parts.Count -eq 2) { $parts[1] } else { "" }
    $relativeDirectory = Split-Path -Parent $pattern
    $items = @(Get-ChildItem -Path (Join-Path $SourceRoot $pattern) -File -ErrorAction SilentlyContinue)
    if ($items.Count -eq 0) {
        throw "Required copy pattern produced no files: $pattern"
    }
    foreach ($item in $items) {
        $destination = if ($explicitDestination) {
            Join-Path $DestinationRoot $explicitDestination
        } else {
            if ($relativeDirectory) {
                Join-Path (Join-Path $DestinationRoot $relativeDirectory) $item.Name
            } else {
                Join-Path $DestinationRoot $item.Name
            }
        }
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
        Copy-Item -LiteralPath $item.FullName -Destination $destination -Force
    }
}

function Copy-LicenseFile {
    param(
        [Parameter(Mandatory)] [object]$Entry,
        [Parameter(Mandatory)] [string]$ToolArchiveRoot,
        [AllowEmptyString()] [string]$SourceArchiveRoot,
        [Parameter(Mandatory)] [string]$LicenseRoot
    )
    $destination = Join-Path $LicenseRoot ([string]$Entry.destination)
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
    if ($Entry.download) {
        $artifact = [pscustomobject]@{
            file = [string]$Entry.download
            url = [string]$Entry.url
            sha256 = [string]$Entry.sha256
        }
        $downloaded = Get-VerifiedArtifact -Artifact $artifact -Label "license $($artifact.file)"
        Copy-Item -LiteralPath $downloaded -Destination $destination -Force
        return
    }
    $root = if ($Entry.archive -eq "sourceArchive") { $SourceArchiveRoot } else { $ToolArchiveRoot }
    $source = Join-Path $root ([string]$Entry.archivePath)
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Declared license/notice file is missing from archive: $source"
    }
    Copy-Item -LiteralPath $source -Destination $destination -Force
}

Reset-WorkspaceDirectory -Path $work
$extractRoot = Join-Path $work "extract"
$jpackageInput = Join-Path $work "jpackage-input"
$jpackageOutput = Join-Path $work "jpackage"
New-Item -ItemType Directory -Force -Path $extractRoot, $jpackageInput, $jpackageOutput | Out-Null

if (-not $SkipBuild) {
    Push-Location $projectRoot
    try {
        & mvn -q -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            throw "Maven package failed."
        }
    } finally {
        Pop-Location
    }
}

$jar = Join-Path $projectRoot "opentools-app\target\opentools.jar"
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw "Application JAR is missing: $jar"
}
Copy-Item -LiteralPath $jar -Destination (Join-Path $jpackageInput "opentools.jar")

$native = if ($NativeBuildDirectory) {
    (Resolve-Path -LiteralPath $NativeBuildDirectory).Path
} else {
    ""
}

$toolArchiveRoots = @{}
$toolSourceRoots = @{}
$toolCachePaths = @{}
$vcRuntime = $manifest.runtimeSupport
$vcRedist = Get-VerifiedArtifact -Artifact $vcRuntime.archive -Label "Microsoft VC runtime"
foreach ($tool in $manifest.tools) {
    $toolRoot = Join-Path $extractRoot $tool.id
    $archivePath = Get-VerifiedArtifact -Artifact $tool.archive -Label "$($tool.name) $($tool.version)"
    $toolCachePaths[$tool.id] = $archivePath
    if ($tool.archive.type -ne "binary") {
        Expand-Artifact -Path $archivePath -Destination $toolRoot -Type $tool.archive.type
    }
    $toolArchiveRoots[$tool.id] = if ($tool.archive.type -eq "binary") { $cache } else {
        if ($tool.archive.root) { Join-Path $toolRoot $tool.archive.root } else { $toolRoot }
    }
    if ($tool.sourceArchive) {
        $sourcePath = Get-VerifiedArtifact -Artifact $tool.sourceArchive -Label "$($tool.name) source"
        $sourceRoot = Join-Path $extractRoot "$($tool.id)-source"
        Expand-Artifact -Path $sourcePath -Destination $sourceRoot -Type $tool.sourceArchive.type
        $toolSourceRoots[$tool.id] = if ($tool.sourceArchive.root) {
            Join-Path $sourceRoot $tool.sourceArchive.root
        } else {
            $sourceRoot
        }
    } elseif ($tool.id -in @("hmmer", "mcscanx")) {
        $toolSourceRoots[$tool.id] = $toolArchiveRoots[$tool.id]
    }
}

if (-not $native) {
    $msysRoot = if ($env:OPENTOOLS_MSYS2_ROOT) {
        $env:OPENTOOLS_MSYS2_ROOT
    } else {
        "C:\msys64"
    }
    $msysBash = Join-Path $msysRoot "usr\bin\bash.exe"
    if (-not (Test-Path -LiteralPath $msysBash -PathType Leaf)) {
        throw "HMMER, trimAl, and MCScanX need a native build. Pass -NativeBuildDirectory or set OPENTOOLS_MSYS2_ROOT to an MSYS2 installation."
    }
    $buildRoot = Join-Path $work "msys-build"
    $nativeOutput = Join-Path $buildRoot "native"
    New-Item -ItemType Directory -Force -Path $buildRoot, $nativeOutput | Out-Null
    Copy-Item -LiteralPath $toolArchiveRoots["hmmer"] -Destination (Join-Path $buildRoot "hmmer-3.4") -Recurse
    Copy-Item -LiteralPath $toolArchiveRoots["mcscanx"] -Destination (Join-Path $buildRoot "MCScanX-1.0.0") -Recurse
    Copy-Item -LiteralPath $toolSourceRoots["trimal"] -Destination (Join-Path $buildRoot "trimal-1.5.1") -Recurse

    $buildScript = Join-Path $projectRoot "scripts\build-msys-dependencies.sh"
    $toUnix = {
        param([string]$Value)
        $previous = $env:OPENTOOLS_PATH_TO_CONVERT
        try {
            $env:OPENTOOLS_PATH_TO_CONVERT = $Value
            (& $msysBash -lc 'cygpath -u "$OPENTOOLS_PATH_TO_CONVERT"').Trim()
        } finally {
            $env:OPENTOOLS_PATH_TO_CONVERT = $previous
        }
    }
    $scriptUnix = & $toUnix $buildScript
    if ($LASTEXITCODE -ne 0 -or -not $scriptUnix) {
        throw "Could not convert the MSYS2 build script path."
    }
    $buildRootUnix = & $toUnix $buildRoot
    $nativeUnix = & $toUnix $nativeOutput
    & $msysBash -lc "bash '$scriptUnix' '$buildRootUnix' '$nativeUnix'"
    if ($LASTEXITCODE -ne 0) {
        throw "MSYS2 native dependency build failed."
    }
    $native = $nativeOutput
}

$cliProperties = Join-Path $work "OpenToolsCLI.properties"
Set-Content -LiteralPath $cliProperties -Encoding ascii -Value "win-console=true"
$jpackageArgs = @(
    "--type", "app-image",
    "--name", "OpenTools",
    "--dest", $jpackageOutput,
    "--input", $jpackageInput,
    "--main-jar", "opentools.jar",
    "--main-class", "io.opentools.app.OpenToolsLauncher",
    "--app-version", $appVersion,
    "--vendor", "OpenTools contributors",
    "--description", "Local-first bioinformatics workbench",
    "--copyright", "Copyright (C) 2026 OpenTools contributors",
    "--icon", (Join-Path $projectRoot "packaging\windows\opentools.ico"),
    "--java-options", "-Dfile.encoding=UTF-8",
    "--java-options", "-Dopentools.packaged=true",
    "--add-launcher", "OpenToolsCLI=$cliProperties"
)
& $jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) {
    throw "jpackage app-image creation failed."
}

$imageRoot = Join-Path $jpackageOutput "OpenTools"
if (-not (Test-Path -LiteralPath (Join-Path $imageRoot "OpenTools.exe") -PathType Leaf)) {
    throw "jpackage did not create OpenTools.exe."
}

Copy-Item -LiteralPath (Join-Path $projectRoot "lang") -Destination (Join-Path $imageRoot "lang") -Recurse
$packageDocs = Join-Path $imageRoot "docs"
New-Item -ItemType Directory -Force -Path $packageDocs | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot "docs\README.md") -Destination $packageDocs
Copy-Item -LiteralPath (Join-Path $projectRoot "docs\en_US") `
    -Destination (Join-Path $packageDocs "en_US") -Recurse
Copy-Item -LiteralPath (Join-Path $projectRoot "docs\zh_CN") `
    -Destination (Join-Path $packageDocs "zh_CN") -Recurse
$packagePlugins = Join-Path $imageRoot "plugins"
New-Item -ItemType Directory -Force -Path $packagePlugins, (Join-Path $packagePlugins "data") | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot "plugins\README.md") -Destination (Join-Path $packagePlugins "README.md")
Copy-Item -LiteralPath (Join-Path $projectRoot "plugins\plugin-state.json") -Destination (Join-Path $packagePlugins "plugin-state.json")
Copy-Item -LiteralPath (Join-Path $projectRoot "opentools-example-plugin\target\opentools-example-plugin.jar") `
    -Destination (Join-Path $packagePlugins "opentools-example-plugin.jar")
Copy-Item -LiteralPath (Join-Path $projectRoot "skills") -Destination (Join-Path $imageRoot "skills") -Recurse
Copy-Item -LiteralPath (Join-Path $projectRoot "examples") -Destination (Join-Path $imageRoot "examples") -Recurse
$pluginSdk = Join-Path $imageRoot "plugin-sdk"
New-Item -ItemType Directory -Force -Path $pluginSdk | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot "opentools-plugin-api\target\opentools-plugin-api-$version.jar") `
    -Destination $pluginSdk
Copy-Item -LiteralPath (Join-Path $projectRoot "opentools-plugin-api\target\opentools-plugin-api-$version-javadoc.jar") `
    -Destination $pluginSdk
Copy-Item -LiteralPath (Join-Path $projectRoot "opentools-plugin-api\target\reports\apidocs") `
    -Destination (Join-Path $pluginSdk "javadoc") -Recurse
Copy-Item -LiteralPath (Join-Path $projectRoot "plugin-sdk\README.txt") `
    -Destination (Join-Path $pluginSdk "README.txt")
Copy-Item -LiteralPath (Join-Path $projectRoot "LICENSE") -Destination (Join-Path $imageRoot "LICENSE")
Copy-Item -LiteralPath (Join-Path $projectRoot "NOTICE") -Destination (Join-Path $imageRoot "NOTICE")
Copy-Item -LiteralPath (Join-Path $projectRoot "THIRD_PARTY_NOTICES.md") -Destination (Join-Path $imageRoot "THIRD_PARTY_NOTICES.md")
Copy-Item -LiteralPath (Join-Path $projectRoot "packaging\windows\README.txt") -Destination (Join-Path $imageRoot "README.txt")

$licensesRoot = Join-Path $imageRoot "licenses"
$toolsRoot = Join-Path $imageRoot "tools"
$sourcesRoot = Join-Path $licensesRoot "sources"
$javaLicenses = Join-Path $licensesRoot "java-dependencies"
New-Item -ItemType Directory -Force -Path $licensesRoot, $toolsRoot, $sourcesRoot, $javaLicenses | Out-Null
Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $licensesRoot "tools-manifest.json")
Copy-Item -LiteralPath (Join-Path $projectRoot "licenses\WRITTEN_SOURCE_OFFER.md") `
    -Destination (Join-Path $licensesRoot "WRITTEN_SOURCE_OFFER.md")

$toolRows = @()
if ($MemeDirectory) {
    $memeRoot = (Resolve-Path -LiteralPath $MemeDirectory).Path
    foreach ($required in @("bin\meme.exe", "bin\mast.exe", "bin\cygwin1.dll")) {
        if (-not (Test-Path -LiteralPath (Join-Path $memeRoot $required) -PathType Leaf)) {
            throw "The MEME directory is missing required packaged file: $required"
        }
    }
    $memeDestination = Join-Path $toolsRoot "meme"
    New-Item -ItemType Directory -Force -Path $memeDestination | Out-Null
    Copy-Item -Path (Join-Path $memeRoot "*") -Destination $memeDestination -Recurse -Force
    $memeLicenseDir = Join-Path $licensesRoot "tools\meme"
    New-Item -ItemType Directory -Force -Path $memeLicenseDir | Out-Null
    if (Test-Path -LiteralPath (Join-Path $memeRoot "LICENSE") -PathType Leaf) {
        Copy-Item -LiteralPath (Join-Path $memeRoot "LICENSE") -Destination (Join-Path $memeLicenseDir "LICENSE") -Force
    }
    $toolRows += [pscustomobject]@{
        Tool = "MEME Suite"
        Version = "5.5.9"
        License = "MEME Suite non-commercial license"
        SHA256 = "0406FB7B1DC27F6AAB3D6D3A29ECDF617BBDD946690CFA97CA2129BC210BFD12"
        Source = "https://meme-suite.org/meme/meme-software/5.5.9/meme-5.5.9.tar.gz"
    }
}

foreach ($tool in $manifest.tools) {
    $destinationRoot = Join-Path $imageRoot $tool.destination
    New-Item -ItemType Directory -Force -Path $destinationRoot | Out-Null
    foreach ($pattern in $tool.copy) {
        if ($pattern -in @("MSVCP140.dll", "bin/MSVCP140.dll")) {
            $systemMsvcp = Join-Path $env:SystemRoot "System32\MSVCP140.dll"
            if (-not (Test-Path -LiteralPath $systemMsvcp -PathType Leaf)) {
                throw "BLAST+ requires MSVCP140.dll, but the Windows build runtime does not contain it."
            }
            $msvcpVersion = [version](Get-Item -LiteralPath $systemMsvcp).VersionInfo.FileVersion
            if ($msvcpVersion -lt [version]$vcRuntime.minimumBuild) {
                throw "MSVCP140.dll version $msvcpVersion is older than required $($vcRuntime.minimumBuild)."
            }
            $msvcpDestination = Join-Path $destinationRoot $pattern
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $msvcpDestination) | Out-Null
            Copy-Item -LiteralPath $systemMsvcp -Destination $msvcpDestination -Force
            continue
        }
        if ($pattern.StartsWith("native/")) {
            if (-not $native) {
                throw "$($tool.name) requires a native build directory. Run build-msys-dependencies.sh or pass -NativeBuildDirectory."
            }
            Copy-ToolPattern -SourceRoot $native -DestinationRoot $destinationRoot `
                -PatternSpec $pattern.Substring("native/".Length)
        } else {
            Copy-ToolPattern -SourceRoot $toolArchiveRoots[$tool.id] -DestinationRoot $destinationRoot `
                -PatternSpec $pattern
        }
    }
    foreach ($required in $tool.requiredFiles) {
        if (-not (Test-Path -LiteralPath (Join-Path $destinationRoot $required) -PathType Leaf)) {
            throw "$($tool.name) is missing required packaged file: $required"
        }
    }

    $licenseDir = Join-Path $licensesRoot "tools\$($tool.id)"
    New-Item -ItemType Directory -Force -Path $licenseDir | Out-Null
    $sourceRoot = if ($toolSourceRoots.ContainsKey($tool.id)) { $toolSourceRoots[$tool.id] } else { "" }
    foreach ($entry in $tool.licenseFiles) {
        Copy-LicenseFile -Entry $entry -ToolArchiveRoot $toolArchiveRoots[$tool.id] `
            -SourceArchiveRoot $sourceRoot -LicenseRoot $licenseDir
    }

    $officialLicenseDir = Join-Path $licenseDir "official-package-notices"
    if ($toolArchiveRoots[$tool.id] -and $tool.archive.type -ne "binary") {
        $noticeFiles = @(Get-ChildItem -LiteralPath $toolArchiveRoots[$tool.id] -Recurse -File |
            Where-Object { $_.Name -match '^(LICEN[CS]E|COPYING|NOTICE|COPYRIGHT)' })
        foreach ($notice in $noticeFiles) {
            $relative = [IO.Path]::GetRelativePath($toolArchiveRoots[$tool.id], $notice.FullName)
            $target = Join-Path $officialLicenseDir $relative
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
            Copy-Item -LiteralPath $notice.FullName -Destination $target -Force
        }
    }

    $buildInfo = @(
        "tool=$($tool.name)",
        "version=$($tool.version)",
        "license=$($tool.license)",
        "licenseUrl=$($tool.licenseUrl)",
        "sourceUrl=$($tool.sourceUrl)",
        "archiveUrl=$($tool.archive.url)",
        "archiveSha256=$($tool.archive.sha256)",
        "build=$($tool.build | ConvertTo-Json -Compress)",
        "buildHost=$env:PROCESSOR_ARCHITECTURE Windows x64",
        "buildDate=$([DateTimeOffset]::UtcNow.ToString('o'))"
    )
    if ($tool.id -in @("blast", "diamond", "iqtree")) {
        $msvcpPath = if ($tool.id -eq "diamond") {
            Join-Path $destinationRoot "MSVCP140.dll"
        } else {
            Join-Path $destinationRoot "bin\MSVCP140.dll"
        }
        $buildInfo += "msvcp140Version=$((Get-Item -LiteralPath $msvcpPath).VersionInfo.FileVersion)"
    }
    Set-Content -LiteralPath (Join-Path $licenseDir "BUILD.txt") -Encoding utf8 -Value $buildInfo
    $toolRows += [pscustomobject]@{
        Tool = $tool.name
        Version = $tool.version
        License = $tool.license
        SHA256 = $tool.archive.sha256
        Source = $tool.sourceUrl
    }

    if (($tool.id -in @("hmmer", "mcscanx")) -or $tool.sourceArchive) {
        $sourceArtifact = if ($tool.sourceArchive) { $tool.sourceArchive } else { $tool.archive }
        $sourceFile = Get-VerifiedArtifact -Artifact $sourceArtifact -Label "$($tool.name) source"
        Copy-Item -LiteralPath $sourceFile -Destination (Join-Path $sourcesRoot $sourceArtifact.file)
    }
}

$javaNotice = @"
# Java runtime and libraries

The private runtime directory is produced by JDK 21 jpackage. JDK legal files are
kept under runtime/legal as supplied by the JDK.

OpenTools' runtime Java libraries are listed in THIRD_PARTY_NOTICES.md. Their
Maven coordinates and versions are declared by pom.xml. The shaded application
JAR retains upstream META-INF notice and license resources when supplied by the
dependency artifacts. Release maintainers must inspect and update the dependency
list for every OpenTools version.
"@
Set-Content -LiteralPath (Join-Path $javaLicenses "README.txt") -Encoding utf8 -Value $javaNotice

$runtimeNotes = @"
# Windows build runtime notices

HMMER and MCScanX are compiled from pinned upstream source. This build may carry
MSYS2/MinGW runtime DLLs required by those executables. Their exact names are in
the corresponding tools directory. MSYS2, Cygwin, GCC runtime, libstdc++, and
mingw-w64 notices must be reviewed and preserved for redistribution. The pinned
source archives and build commands are included here; this file records a
remaining manual legal-review item and does not relicense those runtime files.
"@
Set-Content -LiteralPath (Join-Path $licensesRoot "BUILD_RUNTIME_NOTICES.md") -Encoding utf8 -Value $runtimeNotes

$vcLicenseDir = Join-Path $licensesRoot "microsoft-vc-runtime"
New-Item -ItemType Directory -Force -Path $vcLicenseDir | Out-Null
Copy-Item -LiteralPath $vcRedist -Destination (Join-Path $vcLicenseDir $vcRuntime.archive.file)
$vcNotice = @(
    "Microsoft Visual C++ Redistributable x64",
    "Pinned installer version: $($vcRuntime.version)",
    "Installer SHA-256: $($vcRuntime.archive.sha256)",
    "Official download: $($vcRuntime.archive.url)",
    "License terms: $($vcRuntime.licenseUrl)",
    "Support article: $($vcRuntime.sourceUrl)",
    "Used runtime file: MSVCP140.dll (system build; version is recorded in the BLAST BUILD.txt)",
    "",
    "The redistributable is an independent Microsoft component and is not relicensed by OpenTools. Release maintainers must review current Microsoft redistribution terms."
)
Set-Content -LiteralPath (Join-Path $vcLicenseDir "NOTICE.txt") -Encoding utf8 -Value $vcNotice

$thirdPartyMarkdown = @(
    "# Bundled third-party tools",
    "",
    "| Tool | Version | License | Source | SHA-256 |",
    "| --- | --- | --- | --- | --- |"
)
foreach ($row in $toolRows) {
    $thirdPartyMarkdown += "| $($row.Tool) | $($row.Version) | $($row.License) | $($row.Source) | ``$($row.SHA256)`` |"
}
$thirdPartyMarkdown += ""
$thirdPartyMarkdown += "OpenTools remains GPL-3.0-or-later. Third-party programs retain their own licenses."
Set-Content -LiteralPath (Join-Path $imageRoot "THIRD_PARTY_TOOLS.md") -Encoding utf8 -Value $thirdPartyMarkdown

$archive = Join-Path $output "$packageName.zip"
if (Test-Path -LiteralPath $archive) {
    Remove-Item -LiteralPath $archive -Force
}
Compress-Archive -LiteralPath $imageRoot -DestinationPath $archive -CompressionLevel Optimal

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($archive)
try {
    $entries = @($zip.Entries.FullName)
    foreach ($required in @(
        "OpenTools/OpenTools.exe",
        "OpenTools/OpenToolsCLI.exe",
        "OpenTools/app/opentools.jar",
        "OpenTools/runtime/lib/modules",
        "OpenTools/docs/README.md",
        "OpenTools/docs/en_US/README.md",
        "OpenTools/docs/zh_CN/README.md",
        "OpenTools/licenses/tools-manifest.json",
        "OpenTools/tools/blast/bin/blastn.exe",
        "OpenTools/tools/diamond/diamond.exe",
        "OpenTools/tools/hmmer/bin/hmmscan.exe",
        "OpenTools/tools/muscle/muscle.exe",
        "OpenTools/tools/trimal/trimal.exe",
        "OpenTools/tools/iqtree/bin/iqtree2.exe",
        "OpenTools/tools/mcscanx/MCScanX.exe"
    )) {
        if ($required -notin $entries) {
            throw "Packaged ZIP is missing required entry: $required"
        }
    }
} finally {
    $zip.Dispose()
}

$archiveHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
$archiveInfo = [ordered]@{
    package = [IO.Path]::GetFileName($archive)
    sha256 = $archiveHash
    bytes = (Get-Item -LiteralPath $archive).Length
    builtAt = [DateTimeOffset]::UtcNow.ToString("o")
    javaVersion = [string]$javaVersion
    toolsManifest = "licenses/tools-manifest.json"
    tools = @($toolRows)
}
$archiveInfo | ConvertTo-Json -Depth 8 |
    Set-Content -LiteralPath "$archive.sha256.json" -Encoding utf8

if (-not $KeepWork) {
    Reset-WorkspaceDirectory -Path $work
}

Write-Output $archive
Write-Output $archiveHash
