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
provided separately through package `tools/`, explicit task paths,
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
```

The Windows portable package uses JDK 21 `jpackage`. Maintainers can run
`scripts/package-windows.ps1`; downloads and native builds happen only at build
time and can be reused from the verified cache.
Tagged releases build all four archives on native GitHub Actions runners.

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
