# Extract Sequences from GFF3/GTF

`gxf.sequence.extract` · Genome annotation · v1.1.0

## What this tool does

Extract feature, upstream, downstream, or flanked sequences.

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
| `genome` Genome FASTA | yes | empty | Select a complete real input file; do not submit the placeholder example path. Reference genome whose sequence IDs match the annotation. |
| `annotation` GFF3/GTF | yes | empty | Select a complete real input file; do not submit the placeholder example path. Annotation defining feature coordinates. |
| `featureType` Feature type | no | CDS | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. GFF/GTF type such as CDS, exon, mRNA, or gene. |
| `groupAttribute` Group attribute | no | AUTO | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Attribute used to group segments. Use AUTO for a type-aware default. |
| `mode` Extraction mode | no | FEATURE | Prefer the default mode and switch only when the input type or analysis goal requires it. FEATURE concatenates segments. UPSTREAM and DOWNSTREAM use strand-aware flanks. |
| `upstream` Upstream bases | no | 0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Upstream length relative to feature strand. |
| `downstream` Downstream bases | no | 0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Downstream length relative to feature strand. |
| `output` Output FASTA | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save inside the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing custom output file. |

## Read the result

- Output formats: `FASTA`, `TSV`
- Each FASTA record represents a feature or group, with diagnostics retained; upstream and downstream directions must follow annotation strand.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run gxf.sequence.extract --param genome=C:\data\genome.fasta --param annotation=C:\data\annotation.gff3
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Check unknown sequences, reversed coordinates, out-of-range positions, and missing parent-child features before downstream interpretation.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Check Genome and Annotation Match](tool:gxf.genome.match)
- [Merge or Split Annotation](tool:gxf.merge-split)
- [Convert BED to GFF3](tool:bed.to-gff)

[Back to documentation home](opentools-doc:README.md)
