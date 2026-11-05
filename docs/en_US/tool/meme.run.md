# MEME Motif Discovery

`meme.run` · Sequence · v1.0.0

## What this tool does

Run MEME on a FASTA file, import motif XML, and write a motif summary table.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Inspect, convert, or clean FASTA/FASTQ data before assembly, annotation, alignment, or downstream analysis.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `FASTA`
- Required parameters: `input`
- Check that identifiers are unambiguous and FASTA/FASTQ records are complete; gzip files must preserve the original record structure.

### Input sketch

**input**

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
| `input` Input sequences | yes | empty | Select a complete real input file; do not submit the placeholder example path. DNA or protein FASTA. |
| `molecule` Molecule type | no | DNA | Prefer the default mode and switch only when the input type or analysis goal requires it. Select DNA or protein mode. |
| `motifs` Motif count | no | 10 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum motifs to discover. |
| `minMotifWidth` Minimum motif width | no | 6 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum motif width. |
| `maxMotifWidth` Maximum motif width | no | 50 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum motif width. |
| `threads` Threads | no | 4 | The default is aligned with the external program; increase it only when CPU, memory, and scheduling allow. MEME worker threads. |
| `timeoutMinutes` Timeout minutes | no | 60 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum command runtime. |
| `executablePath` MEME executable | no | empty | Select a complete real input file; do not submit the placeholder example path. Optional explicit executable path. |
| `output` Output XML | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save meme.xml in the run directory. |
| `extraArgs` Extra MEME arguments | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Quote-aware extra CLI arguments. |
| `overwrite` Overwrite outputs | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing existing outputs. |

## Read the result

- Output formats: `MEME XML`, `TSV`
- Results depend on the selected inputs and parameters; review output files, summary metrics, and diagnostics against the experimental design.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.

## Minimal command example

```powershell
opentools run meme.run --param input=C:\data\input.fasta
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- FASTA/FASTQ records, IDs, and base or quality characters must be complete; damaged or truncated records fail the job.
- `extraArgs` cannot override inputs, outputs, databases, remote mode, or thread counts; only algorithm-tuning options are accepted, and `@` response files and `--` are rejected.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Extract FASTA by ID](tool:fasta.extract)
- [FASTA Statistics](tool:fasta.stats)
- [Predict ORFs](tool:sequence.orf)

[Back to documentation home](opentools-doc:README.md)
