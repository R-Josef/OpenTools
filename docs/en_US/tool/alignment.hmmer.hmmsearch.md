# HMMER hmmsearch

`alignment.hmmer.hmmsearch` · Alignment · v1.0.0

## What this tool does

Search a protein FASTA database with a local HMM query using hmmsearch.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Run local alignment, multiple-sequence alignment, phylogenetic modeling, or normalize their outputs.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `HMM model`, `Protein FASTA`
- Required parameters: `hmm`, `sequences`
- Confirm query and subject molecule types and formats; for external tools, check executable paths, versions, threads, and timeouts.

### Input sketch

**hmm**

```text
>seq1
ATGCGTACGT
>seq2
ATGCGTTCGT
```


**sequences**

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
| `hmm` HMM query | yes | empty | Select a complete real input file; do not submit the placeholder example path. HMMER profile or HMM model file. |
| `sequences` Protein sequence database | yes | empty | Select a complete real input file; do not submit the placeholder example path. Protein FASTA searched by the HMM query. |
| `evalue` E-value threshold | no | 1.0E-5 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum full-sequence E-value. |
| `threads` Threads | no | 4 | The default is aligned with the external program; increase it only when CPU, memory, and scheduling allow. hmmsearch worker threads. |
| `timeoutMinutes` Timeout minutes | no | 30 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum command runtime. |
| `hmmerBin` HMMER bin directory | no | empty | Choose a writable directory and check disk space and naming conflicts before batch output. Directory containing hmmsearch. |
| `domtbl` domtblout file | no | empty | It may be left blank for the run directory; check the parent directory and extension for custom output. Leave blank to save in the run directory. |
| `extraArgs` Extra HMMER arguments | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Quote-aware extra CLI arguments. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `HMMER domtblout`
- domtblout records HMM-to-protein-domain hits; note that query and target orientation differs from hmmscan.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.

## Minimal command example

```powershell
opentools run alignment.hmmer.hmmsearch --param hmm=C:\data\hmm.fasta --param sequences=C:\data\sequences.fasta
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- External program versions and arguments can change hits, alignments, or trees; record versions and complete commands for publication.
- `extraArgs` is passed to the external program; check quoting, conflicting options, and version compatibility first.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [trimAl Alignment Trimming](tool:alignment.trim)
- [DIAMOND Protein Alignment](tool:alignment.diamond)
- [Reciprocal Best BLAST Hits](tool:alignment.blast.reciprocal)

[Back to documentation home](opentools-doc:README.md)
