# Gene Density Windows

`gxf.gene-density` · Genome annotation · v1.0.0

## What this tool does

Count gene starts in fixed genomic windows and report genes per megabase.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Inspect or transform genome annotation while validating sequence names, coordinates, and feature relationships.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `GFF3`, `GTF`, `GFF3.gz`, `GTF.gz`
- Required parameters: `input`, `windowSize`, `stepSize`
- Match sequence names to the genome; interpret GFF3/GTF coordinates as 1-based inclusive and BED as 0-based half-open.

### Input sketch

**input**

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
| `input` Input annotation | yes | empty | Select a complete real input file; do not submit the placeholder example path. GFF3 or GTF. |
| `geneTypes` Gene types | no | gene | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Feature types counted as genes. |
| `windowSize` Window size | yes | 10000 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Window size in bases. |
| `stepSize` Step size | yes | 10000 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Window step in bases. |
| `output` Density TSV | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- Each window reports gene starts and genes per megabase; window and step sizes determine resolution and must be interpreted with the genome build.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run gxf.gene-density --param input=C:\data\input.gff3 --param windowSize=10000 --param stepSize=10000
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
