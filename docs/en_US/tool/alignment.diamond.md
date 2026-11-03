# DIAMOND Protein Alignment

`alignment.diamond` · Alignment · v1.0.0

## What this tool does

Build a DIAMOND protein database and run a local blastp-style search.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Run local alignment, multiple-sequence alignment, phylogenetic modeling, or normalize their outputs.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `Protein FASTA`, `FASTA.gz`
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
| `query` Query protein FASTA | yes | empty | Select a complete real input file; do not submit the placeholder example path. Protein query sequences. |
| `subject` Subject protein FASTA | yes | empty | Select a complete real input file; do not submit the placeholder example path. Protein sequences used to build a temporary DIAMOND database. |
| `evalue` E-value threshold | no | 1.0E-5 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum E-value for reported hits. |
| `maxTargetSeqs` Maximum target sequences | no | 10 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum subject hits per query. |
| `threads` Threads | no | 4 | The default is aligned with the external program; increase it only when CPU, memory, and scheduling allow. Number of DIAMOND worker threads. |
| `timeoutMinutes` Timeout minutes | no | 30 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum runtime for each external command. |
| `sensitive` Sensitive mode | no | false | Start with the default and change it only after understanding the explanation. Use DIAMOND --sensitive mode. |
| `diamondPath` DIAMOND executable | no | empty | Select a complete real input file; do not submit the placeholder example path. Optional path to diamond.exe or diamond. |
| `output` Output DIAMOND table | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save inside the run directory. |
| `extraArgs` Extra DIAMOND arguments | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional quote-aware DIAMOND CLI arguments passed without a shell. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing custom output file. |

## Read the result

- Output formats: `DIAMOND tabular`, `TSV`
- DIAMOND hits depend on E-value, bit score, coverage, and database version; a single threshold is not a complete homology conclusion.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.

## Minimal command example

```powershell
opentools run alignment.diamond --param query=C:\data\query.fasta --param subject=C:\data\subject.fasta
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- External program versions and arguments can change hits, alignments, or trees; record versions and complete commands for publication.
- `extraArgs` cannot override inputs, outputs, databases, remote mode, or thread counts; only algorithm-tuning options are accepted, and `@` response files and `--` are rejected.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Filter Table](tool:table.filter)
- [Reciprocal Best BLAST Hits](tool:alignment.blast.reciprocal)

[Back to documentation home](opentools-doc:README.md)
