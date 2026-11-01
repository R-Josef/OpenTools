# Release packages

## General JAR

Use this package when you already have Java 21, need Java-native tasks, manage
your own analysis tools, or deploy CLI/MCP workloads.

It contains no platform binaries. External discovery order is package `tools/`,
explicit task paths, `OPENTOOLS_*` variables, then `PATH`.

The distribution also includes a self-contained Plugin API JAR under
`plugin-sdk/`, together with a Javadoc JAR and browsable HTML. The API is a
compile-only contract and must not be bundled into a plugin.

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
  plugin-sdk/
  licenses/
  THIRD_PARTY_NOTICES.md
  README.txt
```

`OpenTools.exe` is the GUI entry, and `OpenToolsCLI.exe` is the terminal and MCP
entry. Development `.bat` files are not end-user dependencies.

## Platform policy

Only Windows x64 is packaged here. Linux and macOS need separate CI, manifests,
tool sets, and regression results. Do not mix binaries from other platforms into
the Windows package.

## Signing and installers

The portable ZIP is currently unsigned and has no installer or auto-update. An
organization that needs signing should use a separate release process.

## Reproducibility

The pinned manifest records versions, official URLs, SHA-256 values,
destinations, licenses, and source URLs. Build caches can be reused offline and
checksum mismatches stop the package immediately. See `licenses/` and
`tools-manifest.json` in the extracted package.
