# Filter FASTQ

`fastq.filter` · Sequence · v1.2.0

## What this tool does

Filter and trim FASTQ by length, encoded mean quality, and N content with per-record reasons.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Inspect, convert, or clean FASTA/FASTQ data before assembly, annotation, alignment, or downstream analysis.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `FASTQ`, `FASTQ.gz`
- Required parameters: `input`
- Check that identifiers are unambiguous and FASTA/FASTQ records are complete; gzip files must preserve the original record structure.

### Input sketch

**input**

```text
@read1
ACGTACGT
+
IIIIIIII
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Input FASTQ | yes | empty | Select a complete real input file; do not submit the placeholder example path. FASTQ, optionally gzip-compressed. |
| `threads` Threads | no | 0 | Keep `0` for automatic selection on large files; use `1` for small inputs or fixed sequential processing. 0 uses AUTO for large files; 1 disables parallel read processing. |
| `trimLeft` Trim left | no | 0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Bases removed from the 5' end. |
| `trimRight` Trim right | no | 0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Bases removed from the 3' end. |
| `minLength` Minimum length | no | 0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum retained read length. |
| `maxLength` Maximum length | no | 2147483647 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum retained read length. |
| `minMeanQuality` Minimum mean quality | no | 0.0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum mean Phred quality after trimming and decoding. |
| `maxNPercent` Maximum N percent | no | 100.0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum N percentage after trimming. |
| `qualityEncoding` Quality encoding | no | PHRED_33 | Prefer the default mode and switch only when the input type or analysis goal requires it. Encoding used for quality characters; AUTO scans the complete file before filtering. |
| `output` Output FASTQ | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory; .gz enables gzip. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `FASTQ`, `FASTQ.gz`, `TSV report`
- Output reads are trimmed and filtered by length, quality, and N content; trimming changes downstream coordinates, so preserve the source FASTQ.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.

## Minimal command example

```powershell
opentools run fastq.filter --param input=C:\data\input.fastq
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- FASTA/FASTQ records, IDs, and base or quality characters must be complete; damaged or truncated records fail the job.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [FASTQ Statistics](tool:fastq.stats)
- [Convert FASTA and Tables](tool:fasta.convert)

[Back to documentation home](opentools-doc:README.md)
