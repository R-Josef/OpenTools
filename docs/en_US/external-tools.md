# External tools

Java-native tasks do not need external executables. The following adapters call
independent programs:

| Tool | Purpose | Portable directory |
| --- | --- | --- |
| NCBI BLAST+ | Local sequence alignment and database creation | `tools/blast/bin` |
| DIAMOND | Fast protein alignment | `tools/diamond` |
| HMMER | hmmscan, hmmsearch, and HMM preparation | `tools/hmmer/bin` |
| MUSCLE | Multiple sequence alignment | `tools/muscle` |
| trimAl | Alignment trimming | `tools/trimal` |
| IQ-TREE | Maximum-likelihood phylogeny | `tools/iqtree/bin` |
| MCScanX | Collinearity and duplicate-gene classification | `tools/mcscanx` |

## Discovery order

1. package `tools/`;
2. explicit task directory or executable;
3. `OPENTOOLS_*` environment variable;
4. the system `PATH` inherited by the running process;
5. Windows-only fallback `C:\Program Files\OpenToolsDeps\bin`.

The fifth entry is a development-environment fallback. It checks only that one
fixed directory after every preceding lookup fails. It does not scan other
Program Files directories or override a valid explicit path, `OPENTOOLS_*`
value, or PATH result. When the fallback resolves a tool, Settings shows a
warning because the current process PATH did not resolve it.

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
OPENTOOLS_MUSCLE
OPENTOOLS_TRIMAL
OPENTOOLS_IQTREE
OPENTOOLS_MCSCANX
```

Running Explorer, terminal, and OpenTools processes do not receive later
machine-level PATH changes automatically; they retain the environment captured
at process start. For double-click launches, close the old OpenTools process and
restart Explorer, or sign out and back in. Settings checks the PATH actually
inherited by the current OpenTools process and shows the resolved executable
and any fallback warning.

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
