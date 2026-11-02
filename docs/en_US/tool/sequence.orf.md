# Predict ORFs

`sequence.orf` · Sequence · v1.2.0

## What this tool does

Find ORFs with a configurable NCBI genetic code in three forward and optionally three reverse frames.

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
| `input` Input FASTA | yes | empty | Select a complete real input file; do not submit the placeholder example path. Nucleotide FASTA. |
| `threads` Threads | no | 0 | Keep `0` for automatic selection on large files; use `1` for small inputs or fixed sequential processing. 0 uses AUTO for large files; 1 disables parallel record processing. |
| `minAminoAcids` Minimum amino acids | no | 30 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum ORF length. |
| `geneticCode` Genetic code | no | 1 | Prefer the default mode and switch only when the input type or analysis goal requires it. NCBI translation table used for stop codons. |
| `startCodons` Start codons | no | ATG | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Comma or whitespace separated codons. |
| `bothStrands` Both strands | no | true | Start with the default and change it only after understanding the explanation. Scan reverse-complement frames. |
| `includePartial` Include partial ORFs | no | false | Start with the default and change it only after understanding the explanation. Include start-to-end ORFs without a stop codon. |
| `output` Output ORF FASTA | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory; .gz enables gzip. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `FASTA`, `FASTA.gz`
- The output contains predicted ORF nucleotide sequences; inspect genetic code, start codons, minimum length, strand, and partial-ORF behavior.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.

## Minimal command example

```powershell
opentools run sequence.orf --param input=C:\data\input.fasta
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
