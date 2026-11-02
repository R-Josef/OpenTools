# Prepare MCScanX Input

`mcscan.input` · Comparative genomics · v1.0.0

## What this tool does

Create MCScanX .gff and .blast files.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Prepare, run, or organize comparative-genomics analyses such as collinearity and Ka/Ks.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `GFF3/GTF`, `BLAST tabular`
- Required parameters: `annotation`, `blast`
- Confirm that formats, identifiers, headers, and coordinate conventions match the parameter descriptions, and keep the source files.

### Input sketch

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
| `annotation` Annotation | yes | empty | Select a complete real input file; do not submit the placeholder example path. GFF3/GTF with gene features. |
| `blast` BLAST table | yes | empty | Select a complete real input file; do not submit the placeholder example path. Tabular BLAST hits. |
| `outputPrefix` Output prefix | no | mcscan | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Prefix for .gff and .blast outputs. |
| `overwrite` Overwrite outputs | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing existing outputs. |

## Read the result

- Output formats: `GFF`, `BLAST`
- The tool creates `.gff` and `.blast` inputs; check gene IDs, chromosome naming, BLAST orientation, and the generated file prefix.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run mcscan.input --param annotation=C:\data\annotation.gff3 --param blast=C:\data\blast.tsv
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Mismatched formats, columns, coordinates, or resource versions fail before outputs are published.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Run MCScanX](tool:mcscan.run)
- [Local BLAST](tool:alignment.blast.local)

[Back to documentation home](opentools-doc:README.md)
