# Transform FASTA

`fasta.transform` · Sequence · v1.1.0

## What this tool does

Stream FASTA records through strand, complement, case, and invalid-character transformations.

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
| `direction` Direction | no | FORWARD | Prefer the default mode and switch only when the input type or analysis goal requires it. Transform sequence direction. Nucleotide complements preserve case. |
| `caseMode` Letter case | no | KEEP | Prefer the default mode and switch only when the input type or analysis goal requires it. Preserve, uppercase, or lowercase sequence letters. |
| `sequenceType` Sequence type | no | AUTO | Prefer the default mode and switch only when the input type or analysis goal requires it. AUTO infers nucleotide or protein from the sequence alphabet. |
| `cleanInvalid` Clean invalid characters | no | false | Start with the default and change it only after understanding the explanation. Remove characters outside the selected sequence alphabet and report each affected record. |
| `output` Output FASTA | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save inside the run directory. A .gz suffix enables gzip output. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing custom output file. |

## Read the result

- Output formats: `FASTA`, `FASTA.gz`, `TSV report`
- The output contains transformed sequences and a report; verify molecule type, reverse complement, case, and removed invalid characters.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.

## Minimal command example

```powershell
opentools run fasta.transform --param input=C:\data\input.fasta
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
