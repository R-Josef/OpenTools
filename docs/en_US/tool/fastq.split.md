# Split FASTQ File

`fastq.split` · Sequence · v1.0.0

## What this tool does

Split a FASTQ file into parts with a fixed number of records, preserving sequence and quality.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Inspect, convert, or clean FASTA/FASTQ data before assembly, annotation, alignment, or downstream analysis.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `FASTQ`, `FASTQ.gz`
- Required parameters: `input`, `recordsPerFile`
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
| `recordsPerFile` Records per file | yes | 1000 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Number of FASTQ records in each output part. |
| `outputDirectory` Output directory | no | empty | Choose a writable directory and check disk space and naming conflicts before batch output. Leave blank to save parts inside the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing existing part files. |

## Read the result

- Output formats: `FASTQ`
- The output directory contains FASTQ parts split by record count; verify total records, headers, sequences, and qualities.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run fastq.split --param input=C:\data\input.fastq --param recordsPerFile=1000
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
