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

function Get-RelativePathCompat {
    param([string]$Base, [string]$Target)
    $basePath = [IO.Path]::GetFullPath($Base).TrimEnd([IO.Path]::DirectorySeparatorChar)
    $baseUri = [Uri]($basePath + [IO.Path]::DirectorySeparatorChar)
    $targetUri = [Uri][IO.Path]::GetFullPath($Target)
    return [Uri]::UnescapeDataString($baseUri.MakeRelativeUri($targetUri).ToString()).Replace("/", "\")
}
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$manifestPath = Join-Path $projectRoot "packaging\windows\tools.json"
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
$sourceManifestPath = Join-Path $projectRoot "packaging\sources.json"
$sourceManifest = Get-Content -Raw -LiteralPath $sourceManifestPath | ConvertFrom-Json
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
$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    $javaVersion = & $java -version 2>&1 | Select-Object -First 1
} finally {
    $ErrorActionPreference = $previousErrorAction
}
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
            $windowsTar = Join-Path $env:SystemRoot "System32\tar.exe"
            & $windowsTar -xf $Path -C $Destination
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
$systemMsvcp = Join-Path $env:SystemRoot "System32\MSVCP140.dll"
if (-not (Test-Path -LiteralPath $systemMsvcp -PathType Leaf)) {
    throw "Microsoft VC++ runtime is required but MSVCP140.dll is missing from the build host."
}
$systemMsvcpVersion = [version](Get-Item -LiteralPath $systemMsvcp).VersionInfo.FileVersion
if ($systemMsvcpVersion -lt [version]$vcRuntime.minimumBuild) {
    throw "MSVCP140.dll version $systemMsvcpVersion is older than required $($vcRuntime.minimumBuild)."
}
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
    $pacman = Join-Path $msysRoot "usr\bin\pacman.exe"
    if (-not (Test-Path -LiteralPath $pacman -PathType Leaf)) {
        throw "MSYS2 pacman was not found: $pacman"
    }
    foreach ($packageName in @("msys2-runtime", "gcc-libs")) {
        $expectedVersion = [string]$sourceManifest.windowsBuildRuntime.packages.$packageName
        $query = & $pacman -Q $packageName 2>$null
        if ($LASTEXITCODE -ne 0 -or -not $query) {
            throw "Required MSYS2 package is not installed: $packageName"
        }
        $actualVersion = (($query -split "\s+")[1]).Trim()
        if ($actualVersion -ne $expectedVersion) {
            throw "MSYS2 package $packageName is $actualVersion, but sources.json pins $expectedVersion."
        }
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

$sqliteJar = @(
    (Join-Path $imageRoot "app\opentools.jar"),
    (Join-Path $imageRoot "opentools.jar")
) | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
if ($sqliteJar) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $sqliteZip = [IO.Compression.ZipFile]::OpenRead($sqliteJar)
    try {
        $sqliteEntry = $sqliteZip.GetEntry("org/sqlite/native/Windows/x86_64/sqlitejdbc.dll")
        if ($sqliteEntry) {
            $sqliteTarget = Join-Path (Split-Path -Parent $sqliteJar) "sqlitejdbc.dll"
            [IO.Compression.ZipFileExtensions]::ExtractToFile($sqliteEntry, $sqliteTarget, $true)
        }
    } finally {
        $sqliteZip.Dispose()
    }
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
$patchesRoot = Join-Path $licensesRoot "patches"
$runtimeNoticesRoot = Join-Path $licensesRoot "runtime-notices"
$legalRoot = Join-Path $licensesRoot "legal"
$javaLicenses = Join-Path $licensesRoot "java-dependencies"
New-Item -ItemType Directory -Force -Path $licensesRoot, $toolsRoot, $patchesRoot, $runtimeNoticesRoot, $legalRoot, $javaLicenses | Out-Null
Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $licensesRoot "tools-manifest.json")
Copy-Item -LiteralPath (Join-Path $projectRoot "licenses\SOURCE_INFO.md") `
    -Destination (Join-Path $licensesRoot "SOURCE_INFO.md")
Copy-Item -LiteralPath $sourceManifestPath -Destination (Join-Path $licensesRoot "sources.json")
Get-ChildItem -LiteralPath (Join-Path $projectRoot "licenses") -File |
    Where-Object { $_.Extension -eq ".txt" } |
    ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $legalRoot $_.Name)
    }

$toolRows = @()
if ($MemeDirectory) {
    $memeRoot = (Resolve-Path -LiteralPath $MemeDirectory).Path
    $cygwinRuntime = $sourceManifest.components | Where-Object { $_.id -eq "cygwin" }
    if (-not $cygwinRuntime) {
        throw "The source manifest does not declare the Cygwin runtime source."
    }
    $cygwinDll = Join-Path $memeRoot "bin\cygwin1.dll"
    $cygwinVersion = [string](Get-Item -LiteralPath $cygwinDll).VersionInfo.ProductVersion
    if (-not $cygwinVersion.StartsWith([string]$cygwinRuntime.version, [StringComparison]::OrdinalIgnoreCase)) {
        throw "MEME uses Cygwin $cygwinVersion, but sources.json pins $($cygwinRuntime.version)."
    }
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
    Get-ChildItem -LiteralPath $memeRoot -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^(LICEN[CS]E|COPYING|NOTICE|README)' } |
        ForEach-Object {
            Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $memeLicenseDir $_.Name) -Force
        }
    Copy-Item -LiteralPath (Join-Path $projectRoot "scripts\patches\meme-5.5.9-cygwin.patch") `
        -Destination (Join-Path $patchesRoot "meme-5.5.9-cygwin.patch")
    $toolRows += [pscustomobject]@{
        Tool = "MEME Suite"
        Version = "5.5.9"
        License = "MEME Suite non-commercial license"
        SHA256 = "0406FB7B1DC27F6AAB3D6D3A29ECDF617BBDD946690CFA97CA2129BC210BFD12"
        Source = "https://meme-suite.org/meme/meme-software/5.5.9/meme-5.5.9.tar.gz"
        SourceMode = "upstream source plus OpenTools patch"
    }
}

foreach ($tool in $manifest.tools) {
    $destinationRoot = Join-Path $imageRoot $tool.destination
    New-Item -ItemType Directory -Force -Path $destinationRoot | Out-Null
    foreach ($pattern in $tool.copy) {
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
            $relative = Get-RelativePathCompat $toolArchiveRoots[$tool.id] $notice.FullName
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
        $buildInfo += "systemMsvcp140Version=$systemMsvcpVersion"
    }
    Set-Content -LiteralPath (Join-Path $licenseDir "BUILD.txt") -Encoding utf8 -Value $buildInfo
    $toolRows += [pscustomobject]@{
        Tool = $tool.name
        Version = $tool.version
        License = $tool.license
        SHA256 = $tool.archive.sha256
        Source = $tool.sourceUrl
        SourceMode = if ($tool.sourceAvailability -eq "same-release-source-bundle") {
            "same-release source bundle"
        } else {
            "official source URL"
        }
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

$sourceBundleName = "opentools-$version-corresponding-sources.tar.gz"
$runtimeNotes = @"
# Windows build runtime notices

HMMER, trimAl, and MCScanX are compiled from pinned upstream source and carry
MSYS2 runtime DLLs required by those executables. The release pins
MSYS2 runtime $($sourceManifest.windowsBuildRuntime.packages.'msys2-runtime') and
GCC runtime libraries $($sourceManifest.windowsBuildRuntime.packages.'gcc-libs').

MEME and MAST carry Cygwin runtime $($sourceManifest.windowsBuildRuntime.cygwinVersion).
Their exact source archives are published in the same release as
$sourceBundleName. MSYS2, Cygwin, GCC runtime, libstdc++, and mingw-w64 license
terms remain in force and are not relicensed by OpenTools.
"@
Set-Content -LiteralPath (Join-Path $licensesRoot "BUILD_RUNTIME_NOTICES.md") -Encoding utf8 -Value $runtimeNotes

$vcLicenseDir = Join-Path $runtimeNoticesRoot "microsoft-vc-runtime"
New-Item -ItemType Directory -Force -Path $vcLicenseDir | Out-Null
$vcNotice = @(
    "Microsoft Visual C++ Redistributable x64 is a system prerequisite.",
    "Pinned installer metadata version: $($vcRuntime.version)",
    "Installer SHA-256: $($vcRuntime.installer.sha256)",
    "Official download: $($vcRuntime.installer.url)",
    "License terms: $($vcRuntime.licenseUrl)",
    "Support article: $($vcRuntime.sourceUrl)",
    "Used system runtime file: MSVCP140.dll",
    "Build-host runtime version: $systemMsvcpVersion",
    "",
    "OpenTools does not bundle vc_redist.x64.exe or add tool-local Microsoft",
    "runtime DLLs. The private JDK runtime may contain Microsoft runtime",
    "components under the JDK legal files. Users must obtain the prerequisite",
    "runtime from Microsoft. Microsoft components are not relicensed by OpenTools."
)
Set-Content -LiteralPath (Join-Path $vcLicenseDir "NOTICE.txt") -Encoding utf8 -Value $vcNotice

$sourceInfo = @"
# Source bundle

The corresponding source bundle for this release is:

$sourceBundleName

It is published as a separate asset in the same release. See `SOURCE_INFO.md`
for source locations and redistribution guidance.
"@
Set-Content -LiteralPath (Join-Path $licensesRoot "SOURCE_BUNDLE.txt") -Encoding utf8 -Value $sourceInfo

$thirdPartyMarkdown = @(
    "# Bundled third-party tools",
    "",
    "| Tool | Version | License | Source availability | Source | SHA-256 |",
    "| --- | --- | --- | --- | --- | --- |"
)
foreach ($row in $toolRows) {
    $thirdPartyMarkdown += "| $($row.Tool) | $($row.Version) | $($row.License) | $($row.SourceMode) | $($row.Source) | ``$($row.SHA256)`` |"
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
    $entries = @($zip.Entries | ForEach-Object { $_.FullName.Replace("\", "/") })
    foreach ($required in @(
        "OpenTools/OpenTools.exe",
        "OpenTools/OpenToolsCLI.exe",
        "OpenTools/app/opentools.jar",
        "OpenTools/runtime/lib/modules",
        "OpenTools/docs/README.md",
        "OpenTools/docs/en_US/README.md",
        "OpenTools/docs/zh_CN/README.md",
        "OpenTools/licenses/tools-manifest.json",
        "OpenTools/licenses/SOURCE_INFO.md",
        "OpenTools/licenses/SOURCE_BUNDLE.txt",
        "OpenTools/licenses/sources.json",
        "OpenTools/licenses/legal/LGPL-3.0.txt",
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
