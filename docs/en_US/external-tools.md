# External tools

Java-native tools do not need external executables. The following adapters call
independent programs:

| Tool | Purpose | Portable directory |
| --- | --- | --- |
| NCBI BLAST+ | Local sequence alignment and database creation | `tools/blast/bin` |
| DIAMOND | Fast protein alignment | `tools/diamond` |
| HMMER | hmmscan, hmmsearch, and HMM preparation | `tools/hmmer/bin` |
| MEME | Motif discovery | `tools/meme/bin` |
| MAST | Motif scanning | `tools/meme/bin` |
| MUSCLE | Multiple sequence alignment | `tools/muscle` |
| trimAl | Alignment trimming | `tools/trimal` |
| IQ-TREE | Maximum-likelihood phylogeny | `tools/iqtree/bin` |
| MCScanX | Collinearity and duplicate-gene classification | `tools/mcscanx` |

The Windows portable package bundles a Cygwin-built MEME 5.5.9 under
`tools/meme`; the Cygwin runtime DLL is included. Linux/macOS packages currently
leave MEME/MAST optional and can use `tools/meme/bin`, `OPENTOOLS_*`, or PATH.
Detection reports a clear missing diagnostic until the executables are available.
If the executable path contains non-ASCII characters, OpenTools mirrors only the
executable and its DLLs to an ASCII temporary directory; Chinese data and
input/output paths remain supported. Check the MEME Suite license before
redistributing its binaries.

Remote BLAST is Java-native: it calls the NCBI BLAST URL API over HTTPS and does
not need a local executable or database. It uploads the query only when the task
sets `allowUpload=true`.

## Discovery order

1. package `tools/`;
2. explicit tool directory or executable;
3. `OPENTOOLS_*` environment variable;
4. the system `PATH` inherited by the running process.

OpenTools does not scan other fixed directories. If a program is available on
`PATH`, OpenTools can use it. After changing `PATH`, make sure newly started
OpenTools processes inherit the updated environment.

OpenTools performs a bounded version check and records the executable, version,
and arguments in the run manifest. External commands use structured arguments,
not a shell command string.

## Configuration

```text
OPENTOOLS_MAKEBLASTDB
OPENTOOLS_BLASTN
OPENTOOLS_BLASTP
OPENTOOLS_BLASTX
OPENTOOLS_TBLASTN
OPENTOOLS_TBLASTX
OPENTOOLS_DIAMOND
OPENTOOLS_HMMSCAN
OPENTOOLS_HMMSEARCH
OPENTOOLS_MEME
OPENTOOLS_MAST
OPENTOOLS_MUSCLE
OPENTOOLS_TRIMAL
OPENTOOLS_IQTREE
OPENTOOLS_MCSCANX
```

Running Explorer, terminal, and OpenTools processes do not receive later
machine-level PATH changes automatically; they retain the environment captured
at process start. For double-click launches, close the old OpenTools process and
restart Explorer, or sign out and back in. Settings checks the PATH actually
inherited by the current OpenTools process and shows the resolved executable or
a missing-tool diagnostic.

## Failure boundaries

- Wrong architecture, missing DLLs, permissions, or antivirus isolation can
  prevent execution.
- Timeouts terminate the process tree. Very short timeouts are unsuitable for
  large databases or phylogenies.
- HMMER databases must be pressed before hmmscan, with all required sidecars.
- External algorithm behavior is controlled by the upstream version.
- Windows packages do not guarantee compatibility with binaries for other
  platforms.

Third-party programs retain their own licenses. The portable package keeps
license notices, official source URLs, pinned SHA-256 values, and source archives
or source-offer information. Manual legal review is required after version or
build changes.
