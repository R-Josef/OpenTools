# Check Genome and Annotation Match

`gxf.genome.match` · Genome annotation · v1.0.0

## What this tool does

Compare annotation sequence IDs and coordinates with a genome FASTA.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Inspect or transform genome annotation while validating sequence names, coordinates, and feature relationships.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `FASTA`, `FASTA.gz`, `GFF3`, `GTF`
- Required parameters: `genome`, `annotation`
- Match sequence names to the genome; interpret GFF3/GTF coordinates as 1-based inclusive and BED as 0-based half-open.

### Input sketch

**genome**

```text
>seq1
ATGCGTACGT
>seq2
ATGCGTTCGT
```


**annotation**

```text
##gff-version 3
chr1	OpenTools	gene	1	100	.	+	.	ID=gene1
chr1	OpenTools	mRNA	1	100	.	+	.	ID=tx1;Parent=gene1
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `genome` Genome FASTA | yes | empty | Select a complete real input file; do not submit the placeholder example path. Reference genome used by the annotation. |
| `annotation` GFF3/GTF | yes | empty | Select a complete real input file; do not submit the placeholder example path. Annotation to validate. |
| `report` Report TSV | no | empty | It may be left blank for the run directory; check the parent directory and extension for custom output. Optional mismatch report path. |

## Read the result

- Output formats: `TSV`
- The mismatch report lists sequence-name, length, or coordinate problems; resolve annotation and genome-version disagreements before interval analysis.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run gxf.genome.match --param genome=C:\data\genome.fasta --param annotation=C:\data\annotation.gff3
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Check unknown sequences, reversed coordinates, out-of-range positions, and missing parent-child features before downstream interpretation.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Inspect GFF3/GTF](tool:gxf.inspect)
- [Check and Repair Annotation](tool:gxf.check-repair)
- [Extract Sequences from GFF3/GTF](tool:gxf.sequence.extract)

[Back to documentation home](opentools-doc:README.md)
