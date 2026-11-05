# Map Annotation IDs

`gxf.id-map` · Genome annotation · v1.0.0

## What this tool does

Rename selected GFF3/GTF attributes using an old-to-new mapping table.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Inspect or transform genome annotation while validating sequence names, coordinates, and feature relationships.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `GFF3`, `GTF`, `Mapping text`
- Required parameters: `input`, `mapping`
- Match sequence names to the genome; interpret GFF3/GTF coordinates as 1-based inclusive and BED as 0-based half-open.

### Input sketch

**input**

```text
##gff-version 3
chr1	OpenTools	gene	1	100	.	+	.	ID=gene1
chr1	OpenTools	mRNA	1	100	.	+	.	ID=tx1;Parent=gene1
```


**mapping**

```text
old_gene_id	new_gene_id
gene1	geneA
gene2	geneB
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
| `mapping` ID mapping | yes | empty | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. One old-to-new mapping per line, separated by a tab or equals sign. |
| `attributes` Attributes to update | no | ID,Parent,gene_id,transcript_id | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Comma or whitespace separated attribute keys. |
| `output` Output annotation | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `GFF3`, `GTF`, `TSV`
- The output rewrites selected attribute IDs; check duplicate new IDs, parent-child consistency, and unmapped values.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run gxf.id-map --param input=C:\data\input.gff3 --param mapping=old_id=new_id
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Check unknown sequences, reversed coordinates, out-of-range positions, and missing parent-child features before downstream interpretation.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Check Genome and Annotation Match](tool:gxf.genome.match)
- [Gene Density Windows](tool:gxf.gene-density)
- [BED12 Block Analysis](tool:bed.blocks)

[Back to documentation home](opentools-doc:README.md)
