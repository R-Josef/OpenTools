# Gene Structure

`visual.gene-structure` · Visualization · v1.0.0

## What this tool does

Render selected GFF3/GTF genes as a transcript and feature diagram.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Turn validated numeric tables into figures for review or publication while retaining a traceable data path.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `GFF3`, `GTF`, `GFF3.gz`, `GTF.gz`
- Required parameters: `input`
- Confirm that headers, numeric columns, and missing values match the parameters. Review the table before interpreting the figure.

### Input sketch

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
| `input` Input annotation | yes | empty | Select a complete real input file; do not submit the placeholder example path. A GFF3 or GTF annotation file, optionally gzip-compressed. |
| `geneIds` Gene IDs | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional gene IDs separated by new lines, commas, or semicolons. |
| `featureTypes` Feature types | no | exon,CDS | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Comma-separated feature types included in transcript tracks. |
| `maxGenes` Maximum genes | no | 20 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum gene loci included in the plot. |
| `title` Plot title | no | Gene structures | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional title written into the plot model. |
| `subtitle` Plot subtitle | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional subtitle written into the plot model. |
| `width` Image width | no | 1200 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Rendered width in pixels. |
| `height` Image height | no | 800 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Rendered height in pixels. |
| `dpi` PDF DPI | no | 144 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Resolution metadata recorded for the plot export. |
| `exportFormats` Export formats | no | PNG_SVG_PDF | Prefer the default mode and switch only when the input type or analysis goal requires it. Choose PNG, SVG, and PDF artifacts. |
| `exportData` Export feature coordinates | no | true | Start with the default and change it only after understanding the explanation. Write rendered feature coordinates as TSV. |
| `output` Output prefix | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Optional base path for plot model and image outputs. |
| `overwrite` Overwrite outputs | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing existing custom output files. |

## Read the result

- Output formats: `SVG`, `PNG`, `PDF`, `JSON`
- The figure shows transcripts and exon/CDS structure for selected genes; verify representative transcripts and coordinate ranges before comparing isoforms.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- If a processed data table is exported, verify that plotted points, groups, ordering, and filters agree with it.

## Minimal command example

```powershell
opentools run visual.gene-structure --param input=C:\data\input.gff3
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Empty data, all-missing values, non-numeric columns, or no observations on one side of a threshold can produce an empty or misleading figure.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Inspect GFF3/GTF](tool:gxf.inspect)
- [Select Representative Transcripts](tool:gxf.representative)

[Back to documentation home](opentools-doc:README.md)
