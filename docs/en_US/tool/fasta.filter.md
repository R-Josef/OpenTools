# Filter FASTA

`fasta.filter` · Sequence · v1.1.0

## What this tool does

Stream FASTA records and filter by IDs, regular expressions, length, GC, or N content.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Inspect, convert, or clean FASTA/FASTQ data before assembly, annotation, alignment, or downstream analysis.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `FASTA`, `FASTA.gz`
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
| `threads` Threads | no | 0 | Keep `0` for automatic selection on large files; use `1` for small inputs or fixed sequential processing. 0 uses AUTO for large files; 1 disables parallel record processing. |
| `includeIds` Include IDs | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional IDs separated by whitespace, commas, or semicolons. Blank keeps all IDs. |
| `excludeIds` Exclude IDs | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional IDs separated by whitespace, commas, or semicolons. |
| `includeRegex` Include ID regex | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional Java regular expression. A record is kept when the regex is found in its ID token. |
| `excludeRegex` Exclude ID regex | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional Java regular expression. Matching ID tokens are filtered out. |
| `ignoreCase` Ignore ID case | no | true | Start with the default and change it only after understanding the explanation. Apply ID and regular-expression matching without case sensitivity. |
| `minLength` Minimum length | no | 0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum sequence length, inclusive. |
| `maxLength` Maximum length | no | 2147483647 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum sequence length, inclusive. |
| `minGcPercent` Minimum GC percent | no | 0.0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum GC percentage. GC is calculated over A/C/G/T/U bases. |
| `maxGcPercent` Maximum GC percent | no | 100.0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum GC percentage. GC is calculated over A/C/G/T/U bases. |
| `minNPercent` Minimum N percent | no | 0.0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum N percentage over all sequence characters. |
| `maxNPercent` Maximum N percent | no | 100.0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum N percentage over all sequence characters. |
| `output` Output FASTA | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save inside the run directory. A .gz suffix enables gzip output. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing custom output file. |

## Read the result

- Output formats: `FASTA`, `FASTA.gz`, `TSV report`
- The output retains records satisfying all active filters, with reasons in the report; document threshold changes in run parameters.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.

## Minimal command example

```powershell
opentools run fasta.filter --param input=C:\data\input.fasta
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- FASTA/FASTQ records, IDs, and base or quality characters must be complete; damaged or truncated records fail the job.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [FASTA Statistics](tool:fasta.stats)
- [Extract FASTA Subsequences](tool:fasta.subsequence)
- [Convert FASTA and Tables](tool:fasta.convert)

[Back to documentation home](opentools-doc:README.md)
