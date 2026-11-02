# Release packages

## General JAR

Use this package when you already have Java 21, need Java-native tools, manage
your own analysis tools, or deploy CLI/MCP workloads.

It contains no platform binaries. External discovery order is package `tools/`,
explicit tool paths, `OPENTOOLS_*` variables, then `PATH`.

The distribution also includes a self-contained Plugin API JAR under
`plugin-sdk/`, together with a Javadoc JAR and browsable HTML. The API is a
compile-only contract and must not be bundled into a plugin.

The trusted example plugin is bundled under `plugins/` and starts enabled by
default. It contributes the interactive Coin Runner tool with English and
Chinese resources packaged in its own JAR.

Plugin API 2.1 keeps plugin-localized descriptors, pages, tools, parameters,
and results inside `META-INF/opentools/i18n/`. Release verification checks the
default plugin JAR, its language resources, and `plugins/plugin-state.json`.

## Windows x64 portable package

Use this package on Windows 10/11 x64 when you want a fixed, self-contained
environment without Java, conda, or MSYS2 setup.

```text
opentools-<version>-windows-x64/
  OpenTools.exe
  OpenToolsCLI.exe
  app/
  runtime/
  tools/
    blast/
    diamond/
    hmmer/
    muscle/
    trimal/
    iqtree/
    mcscanx/
  docs/
    en_US/
    zh_CN/
  plugins/
    opentools-example-plugin.jar
  plugin-sdk/
  licenses/
  THIRD_PARTY_NOTICES.md
  README.txt
```

`OpenTools.exe` is the GUI entry, and `OpenToolsCLI.exe` is the terminal and MCP
entry. Development `.bat` files are not end-user dependencies.

## Linux x64 portable package

```text
opentools-<version>-linux-x64/
  bin/
    OpenTools
    OpenToolsCLI
  lib/
    app/
    runtime/
    tools/
    docs/
    plugins/
    licenses/
```

Extract the complete archive and run `bin/OpenTools` or `bin/OpenToolsCLI`.

## macOS portable packages

Separate `macos-x64` and `macos-arm64` archives contain a complete
`OpenTools.app` bundle. The GUI launcher is `Contents/MacOS/OpenTools`; terminal
and MCP use `Contents/MacOS/OpenToolsCLI`.

## Platform policy

Windows x64, Linux x64, macOS Intel, and macOS Apple Silicon use independent
manifests and tool sets. Tagged releases are built on native GitHub Actions
runners. Never mix binaries from another platform into a package.

## Building release archives

Use a native runner or VM for each target platform. Start with:

```bash
mvn clean verify
```

Windows x64:

```powershell
./scripts/package-windows.ps1
```

The Windows package uses JDK 21 `jpackage`. CI installs MSYS2 with `make` and
`gcc`; local maintainers may pass `-NativeBuildDirectory` to reuse native
HMMER, trimAl, and MCScanX builds.

Linux x64:

```bash
sudo apt-get install -y autoconf automake build-essential file jq \
  libbz2-dev libcurl4-openssl-dev liblzma-dev libtool unzip zlib1g-dev
export JAVA_HOME=/path/to/jdk-21
./scripts/package-linux.sh
```

macOS Intel or Apple Silicon:

```bash
brew install jq
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./scripts/package-macos.sh x64
./scripts/package-macos.sh arm64
```

Run only the command matching the current Mac architecture. Each packaging
script downloads pinned artifacts into `.release-cache/<release>`, verifies
checksums, builds HMMER/trimAl/MCScanX from source, runs the packaged CLI
doctor check, and writes the archive plus checksum metadata to
`target/release/`.

Common Unix packaging options:

```text
--cache-dir PATH
--native-build-dir PATH
--output-dir PATH
--offline
--skip-build
--keep-work
```

`--offline` requires all archives to be cached. `--skip-build` reuses
`opentools-app/target/opentools.jar`. The staged application is kept under
`target/release-work-<release>/` when `--keep-work` is used.

The `Build release packages` GitHub Actions workflow can be started manually to
validate all four builds without publishing. Pushing a version tag builds and
validates every platform, then creates or updates the matching GitHub Release
with all archives and checksum JSON files.

## Signing and installers

The portable archives currently have no installer or auto-update. Windows and
Linux archives are unsigned; macOS snapshot packages use ad-hoc signing and are
not notarized. Developer ID signing and notarization require separate release
credentials.

## Reproducibility

The pinned manifest records versions, official URLs, SHA-256 or official NCBI
MD5 values,
destinations, licenses, and source URLs. Build caches can be reused offline and
checksum mismatches stop the package immediately. See `licenses/` and
`tools-manifest.json` in the extracted package.
