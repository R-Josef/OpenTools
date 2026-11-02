# OpenTools

English | [简体中文](README.zh-CN.md)

OpenTools is a local-first, tool-driven bioinformatics workbench for Windows and
Java 21. It brings common sequence, annotation, table, alignment, expression,
enrichment, HTS, and visualization tools into one searchable workspace. The same
tool definitions power the desktop app, CLI, and MCP server.

The project is designed for bench scientists, bioinformatics analysts, and plugin
developers. Research data stays local by default. OpenTools is licensed under
GPL-3.0-or-later; bundled third-party programs retain their own licenses.

![OpenTools tool catalog](docs/en_US/assets/screenshots/tools-catalog.jpg)

## Features

- 86 built-in tools covering FASTA/FASTQ, GFF3/GTF, BED, tables, BLAST/DIAMOND,
  HMMER, multiple sequence alignment, phylogeny, comparative genomics,
  expression, enrichment, SAM/BAM/CRAM, VCF/BCF, and plotting.
- Projects, jobs, input hashes, parameters, logs, and result manifests are
  persisted together for reproducible reruns.
- GUI, CLI, and MCP share `ToolDescriptor`, validation, tool execution, and
  result contracts.
- The in-app Docs view follows the active UI language and renders bundled
  Markdown fully offline.
- Java-native tools require no external executable. BLAST+, DIAMOND, HMMER,
  MUSCLE, trimAl, IQ-TREE, and MCScanX are available through external adapters.

## Quick links

| Entry | Description |
| --- | --- |
| Windows portable package | `OpenTools.exe` GUI and `OpenToolsCLI.exe`; private Java runtime and Windows x64 tools |
| General JAR | Java 21 users with separately managed analysis tools |
| [User documentation](docs/en_US/README.md) | Quick start, tool reference, formats, CLI/MCP, FAQ |
| [CLI](docs/en_US/cli.md) | Discovery, validation, execution, jobs, and JSON output |
| [MCP](docs/en_US/mcp.md) | stdio and authenticated loopback HTTP |
| [Plugin development](docs/en_US/plugin-development.md) | Plugin API, lifecycle, and examples |

## Release packages

1. **General JAR**: runs Java-native tools and discovers external executables
   from package `tools/`, explicit paths, `OPENTOOLS_*` variables, or `PATH`.
2. **Windows x64 portable package**: starts from `OpenTools.exe`, includes a
   private Java runtime and pinned Windows tools, and does not require conda or
   MSYS2 for end users.
3. **Linux x64 portable package**: extracts to a self-contained directory with
   a private Java runtime and pinned Linux tools.
4. **macOS portable packages**: separate Intel x64 and Apple Silicon arm64 ZIPs,
   each containing a self-contained application bundle.

If an external program is available on `PATH`, OpenTools can use it. Restart
Explorer or sign out after changing PATH so newly launched processes inherit it.

Windows x64, Linux x64, macOS x64, and macOS arm64 releases are built by the
platform matrix in `.github/workflows/release.yml`. Installers, automatic
updates, and macOS Developer ID signing are not included yet.

## First run

1. Download the package matching Windows, Linux, macOS Intel, or macOS Apple
   Silicon.
2. Extract the complete archive.
3. Start the GUI with `OpenTools.exe`, `bin/OpenTools`, or `OpenTools.app`.
4. Use `OpenToolsCLI.exe`, `bin/OpenToolsCLI`, or
   `OpenTools.app/Contents/MacOS/OpenToolsCLI` for terminals and MCP.
5. Open **Docs** from the main navigation at any time.

Building from source:

```powershell
$env:JAVA_HOME = '<JDK 21 installation directory>'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean verify
```

See [Installation](docs/en_US/installation.md) and
[release packages](docs/en_US/release-packages.md) for details.

## External tools

Bundled tools are discovered from the package first. Developer builds may also
use explicit paths, `OPENTOOLS_*` variables, or `PATH`. Exact versions, official
URLs, checksums, licenses, source URLs, and build recipes are in the platform
manifests under [packaging](packaging/).

Third-party executables are independent programs and are not relicensed as
OpenTools. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the package
`licenses/` directory.

## Documentation

- [Language selector](docs/README.md)
- [English user guide](docs/en_US/README.md)
- [中文用户指南](docs/zh_CN/README.md)
- [Tool reference](docs/en_US/tool-reference.md)
- [UI and Docs development](docs/en_US/ui-and-docs.md)
- [Changelog](CHANGELOG.md)

The built-in Markdown browser defaults to the current UI language. `en_US` reads
from `docs/en_US`; Simplified Chinese (`zh_CN`) reads from `docs/zh_CN`.

## License

OpenTools is distributed under [GPL-3.0-or-later](LICENSE). Bundled third-party
programs keep their upstream licenses. Release maintainers must review the
license, source-offer, and runtime redistribution obligations for every update.
