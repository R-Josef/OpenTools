# Extract FASTA by ID

`fasta.extract` · Sequence · v1.0.0

## What this tool does

Extract complete FASTA records using an ID list.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Inspect, convert, or clean FASTA/FASTQ data before assembly, annotation, alignment, or downstream analysis.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `FASTA`, `FASTA.gz`, `Text ID list`
- Required parameters: `input`, `ids`
- Check that identifiers are unambiguous and FASTA/FASTQ records are complete; gzip files must preserve the original record structure.

### Input sketch

**input**

```text
>seq1
ATGCGTACGT
>seq2
ATGCGTTCGT
```


**ids**

```text
seq1
seq2
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Input FASTA | yes | empty | Select a complete real input file; do not submit the placeholder example path. The FASTA file to search. |
| `ids` IDs | yes | empty | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. One sequence ID per line. Lines starting with # are ignored. |
| `matchMode` Match mode | no | ID_TOKEN | Prefer the default mode and switch only when the input type or analysis goal requires it. Choose how each selector is compared with a FASTA header. |
| `ignoreCase` Ignore case | no | true | Start with the default and change it only after understanding the explanation. Compare IDs without case sensitivity. |
| `output` Output FASTA | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save inside the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing custom output file. |

## Read the result

- Output formats: `FASTA`, `Text`
- Only matching complete FASTA records are emitted; requested IDs absent from the report indicate naming mismatches or missing records.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run fasta.extract --param input=C:\data\input.fasta --param ids=seq1
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- FASTA/FASTQ records, IDs, and base or quality characters must be complete; damaged or truncated records fail the job.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [FASTA Statistics](tool:fasta.stats)
- [Split FASTA File](tool:fasta.split)
- [Extract FASTA Subsequences](tool:fasta.subsequence)

[Back to documentation home](opentools-doc:README.md)
