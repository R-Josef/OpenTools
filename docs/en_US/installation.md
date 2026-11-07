# Installation and updates

## Windows x64 portable package

Keep the extracted package together:

```text
opentools-<version>-windows-x64/
  OpenTools.exe
  OpenToolsCLI.exe
  app/
  runtime/
  tools/
  docs/
    en_US/
    zh_CN/
  licenses/
  THIRD_PARTY_NOTICES.md
  README.txt
```

Start the GUI with `OpenTools.exe`. Use `OpenToolsCLI.exe` for terminals and MCP.
Java, conda, and MSYS2 are not required for the portable package because it uses
its own runtime and `tools/` directory.

Do not start the package directly from a ZIP viewer and do not copy
`OpenTools.exe` by itself. The current portable ZIP is unsigned and has no
installer or automatic updater.

## Linux x64 portable package

Extract the complete `opentools-<version>-linux-x64.tar.gz` archive. Start the
GUI with `bin/OpenTools`, or use `bin/OpenToolsCLI` in a terminal:

```bash
tar -xzf opentools-<version>-linux-x64.tar.gz
cd opentools-<version>-linux-x64
bin/OpenToolsCLI --version
bin/OpenTools
```

The archive includes its own Java runtime and Linux tools. Keep `app/`, `lib/`,
`tools/`, `docs/`, and `licenses/` together.

## macOS portable packages

Choose `macos-x64` for Intel Macs and `macos-arm64` for Apple Silicon. Extract
the ZIP and open `OpenTools.app`. For terminal or MCP use:

```bash
OpenTools.app/Contents/MacOS/OpenToolsCLI --version
```

Current snapshot packages are not notarized. Gatekeeper policy may require a
signed and notarized organizational build.

## General JAR

The general JAR requires Java 21. Keep the application, language, documentation,
and plugin directories from the release. External analysis tools must be
provided separately through package `tools/`, explicit tool paths,
`OPENTOOLS_*` variables, or `PATH`.

```powershell
java -jar opentools.jar
java -jar opentools.jar tools list
java -jar opentools.jar doctor --json
```

## Build from source

Install JDK 21 and point `JAVA_HOME` to it. Alternatively, configure your local
`PATH` so that `java`, `mvn`, and `jpackage` can be invoked directly.

```powershell
$env:JAVA_HOME = '<JDK 21 installation directory>'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean verify
```

Important outputs:

```text
opentools-app/target/opentools.jar
opentools-app/target/dist/
target/release/opentools-<version>-windows-x64.zip
target/release/opentools-<version>-linux-x64.tar.gz
target/release/opentools-<version>-macos-x64.zip
target/release/opentools-<version>-macos-arm64.zip
target/release/opentools-<version>-corresponding-sources.tar.gz
```

The Windows portable package uses JDK 21 `jpackage`. Maintainers can run
`scripts/package-windows.ps1`; downloads and native builds happen only at build
time and can be reused from the verified cache. The default cache is
`.release-cache/windows`; pass `-CacheDirectory` to use another location.
Tagged releases build all four archives on native GitHub Actions runners.
The source job also creates one shared corresponding-source archive for all
platform releases.

### Building third-party tools from source

Release packages populate `tools/` at build time. Developers who want to
compile the external tools themselves can use the same scripts:

- **Linux/macOS:** `scripts/package-linux.sh` and
  `scripts/package-macos.sh x64|arm64` download the pinned source archives and
  build HMMER, MCScanX, and trimAl automatically. To reuse prebuilt binaries,
  pass `--native-build-dir /path/to/native`. The lower-level script is
  `scripts/build-unix-dependencies.sh`; it reads `HMMER_SOURCE_DIR`,
  `MCSCANX_SOURCE_DIR`, and `TRIMAL_SOURCE_DIR`.
- **Windows HMMER/MCScanX/trimAl:** run
  `scripts/build-msys-dependencies.sh TOOL_ROOT OUTPUT_DIR` from an MSYS2 shell.
  `TOOL_ROOT` must contain `hmmer-3.4`, `MCScanX-1.0.0`, and
  `trimal-1.5.1/source`. Then package with
  `scripts/package-windows.ps1 -NativeBuildDirectory OUTPUT_DIR`.
- **Windows MEME/MAST:** run `scripts/build-meme-cygwin.sh OUTPUT_DIR` from
  Cygwin with `gcc`, `make`, `perl`, and the libxml2/libxslt/gd development
  packages. It downloads and verifies MEME 5.5.9, applies the Cygwin patch, and
  writes a portable tree. Package it with
  `scripts/package-windows.ps1 -MemeDirectory OUTPUT_DIR`.

For local development, copy a build output to `tools/<tool>` so the normal
discovery order finds it, or point to it with an explicit executable path,
`OPENTOOLS_*`, or `PATH`. Do not commit compiled tool binaries to Git. The
scripts cache downloads under `.release-cache/<release>`. BLAST+, DIAMOND,
IQ-TREE, and MUSCLE are currently installed from pinned upstream binaries, not
built by these source scripts; build them manually only if you also update the
matching manifest and notices. Run `scripts/package-sources.sh` to generate the
separate corresponding-source archive used by the release.

## Updating

1. Exit OpenTools and wait for active jobs.
2. Back up projects and custom plugins.
3. Extract the new version into a new directory.
4. Start the new version and check projects and plugins.
5. Remove the old directory after migration is complete.

Custom language files in `lang/` should be compared with new keys after every
upgrade.

## Uninstalling

There is no system installation for the portable package. Exit the app and
delete the extracted directory. Decide separately whether to keep projects,
user settings, and external tools.
