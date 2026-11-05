# Primer Check (e-PCR)

`sequence.primer-check` · Sequence · v1.0.0

## What this tool does

Locate primer pairs on a genome and report predicted amplicons.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Inspect, convert, or clean FASTA/FASTQ data before assembly, annotation, alignment, or downstream analysis.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `FASTA`, `TSV`
- Required parameters: `genome`, `primers`
- Check that identifiers are unambiguous and FASTA/FASTQ records are complete; gzip files must preserve the original record structure.

### Input sketch

**genome**

```text
>seq1
ATGCGTACGT
>seq2
ATGCGTTCGT
```


**primers**

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
| `genome` Genome FASTA | yes | empty | Select a complete real input file; do not submit the placeholder example path. Nucleotide genome or contigs; both strands are searched. |
| `primers` Primer table | yes | empty | Select a complete real input file; do not submit the placeholder example path. TSV with name, forward, and reverse columns; IUPAC bases are accepted. |
| `maxMismatches` Maximum mismatches | no | 0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Allowed mismatches per primer; 0 requires exact matches. |
| `minProductSize` Minimum product size | no | 50 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum amplicon length in bp. |
| `maxProductSize` Maximum product size | no | 3000 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum amplicon length in bp. |
| `maxAmplicons` Maximum amplicons | no | 100000 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Stop after this many products and mark the result truncated. |
| `output` Output TSV | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- Results depend on the selected inputs and parameters; review output files, summary metrics, and diagnostics against the experimental design.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run sequence.primer-check --param genome=C:\data\genome.fasta --param primers=C:\data\primers.fasta
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
