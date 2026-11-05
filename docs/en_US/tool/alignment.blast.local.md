# Local BLAST

`alignment.blast.local` · Alignment · v1.3.0

## What this tool does

Build a BLAST database and compare query sequences with a local subject FASTA.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Run local alignment, multiple-sequence alignment, phylogenetic modeling, or normalize their outputs.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `FASTA`, `FASTA.gz`
- Required parameters: `query`, `subject`
- Confirm query and subject molecule types and formats; for external tools, check executable paths, versions, threads, and timeouts.

### Input sketch

**query**

```text
>seq1
ATGCGTACGT
>seq2
ATGCGTTCGT
```


**subject**

```text
>seq1
ATGCGTACGT
>seq2
ATGCGTTCGT
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `query` Query FASTA | yes | empty | Select a complete real input file; do not submit the placeholder example path. Query sequences. |
| `subject` Subject FASTA | yes | empty | Select a complete real input file; do not submit the placeholder example path. Subject sequences used to build a temporary BLAST database. |
| `program` BLAST program | no | AUTO | Prefer the default mode and switch only when the input type or analysis goal requires it. AUTO selects a program from query and subject sequence alphabets. |
| `evalue` E-value threshold | no | 1.0E-5 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum E-value for reported hits. |
| `maxTargetSeqs` Maximum target sequences | no | 10 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum subject hits per query. |
| `threads` Threads | no | 4 | The default is aligned with the external program; increase it only when CPU, memory, and scheduling allow. Number of BLAST worker threads. |
| `timeoutMinutes` Timeout minutes | no | 30 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum runtime for each external command. |
| `output` Output BLAST XML | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save blast.xml; tabular and alignment TSVs are derived beside it. |
| `blastBin` BLAST bin directory | no | empty | Choose a writable directory and check disk space and naming conflicts before batch output. Directory containing BLAST+ executables. |
| `cacheEnabled` Reuse BLAST database | no | true | Start with the default and change it only after understanding the explanation. Reuse a database keyed by subject hash, makeblastdb version, program, and database type. |
| `refreshDatabase` Refresh BLAST cache | no | false | Start with the default and change it only after understanding the explanation. Rebuild the matching cache entry before running BLAST. |
| `cacheDirectory` BLAST cache directory | no | empty | Choose a writable directory and check disk space and naming conflicts before batch output. Optional existing writable directory; defaults to the OpenTools system temporary cache. |
| `cacheMaxMegabytes` BLAST cache size limit | no | 10240 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Evict least-recently-used database entries above this size in MiB. |
| `cacheMaxEntries` BLAST cache entry limit | no | 128 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Evict least-recently-used database entries above this count. |
| `extraArgs` Extra BLAST arguments | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional quote-aware BLAST CLI arguments passed without a shell. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing custom output file. |

## Read the result

- Output formats: `BLAST XML`, `TSV`
- The primary output is BLAST XML with derived tabular and alignment TSVs; verify cache identity, E-value, coverage, and program type, then open the BLAST viewer in the Results panel.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.

## Minimal command example

```powershell
opentools run alignment.blast.local --param query=C:\data\query.fasta --param subject=C:\data\subject.fasta
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- External program versions and arguments can change hits, alignments, or trees; record versions and complete commands for publication.
- `extraArgs` cannot override inputs, outputs, databases, remote mode, or thread counts; only algorithm-tuning options are accepted, and `@` response files and `--` are rejected.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Convert BLAST XML to Table](tool:alignment.blast.xml-to-table)
- [Reciprocal Best BLAST Hits](tool:alignment.blast.reciprocal)

[Back to documentation home](opentools-doc:README.md)
