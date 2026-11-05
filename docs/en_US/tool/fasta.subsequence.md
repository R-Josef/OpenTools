# Extract FASTA Subsequences

`fasta.subsequence` · Sequence · v1.1.0

## What this tool does

Extract whole records or 1-based inclusive regions by ID, coordinate, and strand.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Inspect, convert, or clean FASTA/FASTQ data before assembly, annotation, alignment, or downstream analysis.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `FASTA`, `FASTA.gz`, `Region table`
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
| `input` Input FASTA | yes | empty | Select a complete real input file; do not submit the placeholder example path. A FASTA file, optionally gzip-compressed. |
| `regions` Inline regions | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. One request per line: ID, or ID with tab-separated START, END, optional STRAND, and optional OUTPUT_ID. Coordinates are 1-based inclusive. |
| `regionsFile` Region file | no | empty | Select a complete real input file; do not submit the placeholder example path. Optional plain or gzip-compressed file using the same tab-separated request format. |
| `output` Output FASTA | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save inside the run directory. A .gz suffix enables gzip output. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing custom output file. |

## Read the result

- Output formats: `FASTA`, `FASTA.gz`, `TSV diagnostics`
- The output contains sequences requested by ID, coordinates, and strand, plus diagnostics; verify negative-strand and 1-based inclusive boundaries.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run fasta.subsequence --param input=C:\data\input.fasta
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- FASTA/FASTQ records, IDs, and base or quality characters must be complete; damaged or truncated records fail the job.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Extract FASTA by ID](tool:fasta.extract)
- [FASTA Statistics](tool:fasta.stats)
- [Predict ORFs](tool:sequence.orf)

[Back to documentation home](opentools-doc:README.md)
