# Rename FASTA Identifiers

`fasta.rename` · Sequence · v1.1.0

## What this tool does

Prefix, suffix, map, or regex-rename FASTA ID tokens while detecting duplicates.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Inspect, convert, or clean FASTA/FASTQ data before assembly, annotation, alignment, or downstream analysis.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `FASTA`, `FASTA.gz`, `Mapping text`
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
| `input` Input FASTA | yes | empty | Select a complete real input file; do not submit the placeholder example path. FASTA, optionally gzip-compressed. |
| `threads` Threads | no | 0 | Keep `0` for automatic selection on large files; use `1` for small inputs or fixed sequential processing. 0 uses AUTO for large files; 1 disables parallel record processing. |
| `mode` Rename mode | no | PREFIX_SUFFIX | Prefer the default mode and switch only when the input type or analysis goal requires it. Choose the identifier transformation. |
| `idPrefix` ID prefix | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Text prepended in PREFIX_SUFFIX mode. |
| `idSuffix` ID suffix | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Text appended in PREFIX_SUFFIX mode. |
| `mapping` ID mapping | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. One old-to-new pair per line, separated by tab or equals. |
| `searchRegex` Search regex | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. REGEX mode Java regular expression. |
| `replacement` Replacement | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. REGEX mode replacement. |
| `duplicatePolicy` Duplicate IDs | no | APPEND | Prefer the default mode and switch only when the input type or analysis goal requires it. Handle duplicate output identifiers. |
| `output` Output FASTA | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory; .gz enables gzip. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `FASTA`, `FASTA.gz`, `TSV report`
- The output contains renamed FASTA plus a report; check duplicate IDs, unmapped entries, and unintended regex replacements.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.

## Minimal command example

```powershell
opentools run fasta.rename --param input=C:\data\input.fasta
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- FASTA/FASTQ records, IDs, and base or quality characters must be complete; damaged or truncated records fail the job.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Extract FASTA by ID](tool:fasta.extract)
- [FASTA Statistics](tool:fasta.stats)
- [Split FASTA File](tool:fasta.split)

[Back to documentation home](opentools-doc:README.md)
