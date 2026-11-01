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
Java, conda, MSYS2, and `C:\Program Files\OpenToolsDeps` are not required for the
portable package because it uses its own runtime and `tools/` directory.

Do not start the package directly from a ZIP viewer and do not copy
`OpenTools.exe` by itself. The current portable ZIP is unsigned and has no
installer or automatic updater.

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

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean verify
```

Important outputs:

```text
opentools-app/target/opentools.jar
opentools-app/target/dist/
target/release/opentools-<version>-windows-x64.zip
```

The Windows portable package uses JDK 21 `jpackage`. Maintainers can run
`scripts/package-windows.ps1`; downloads and native builds happen only at build
time and can be reused from the verified cache.

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
