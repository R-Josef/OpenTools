# eFP Expression

`plot.efp` · Visualization · v1.1.0

## What this tool does

Render a tissue-grouped expression map with a colored sample band, reproducible sample ordering, and a heatmap of gene expression.

Unlike the generic heatmap, eFP can read a sample map, sort samples by tissue, and draw one colored band per tissue above the matrix.

## When to use it

- Show tissue or developmental-stage expression for selected genes.
- Group samples by tissue while keeping a publication-ready heatmap.
- Export a reordered matrix that matches the figure exactly.

## Prepare the input

- Declared formats: `TSV`, `CSV`
- Expression matrix: genes in rows, samples in the header.
- Optional sample map: tab-separated `sample` and `tissue` columns.
- Samples absent from the map are grouped as `Unassigned`.

### Input sketch

```text
gene	root1	leaf1	root2	leaf2
gene1	12	18	15	20
gene2	40	36	44	31
```

```text
sample	tissue
root1	root
leaf1	leaf
root2	root
leaf2	leaf
```

## Recommended workflow

1. Check that matrix sample names exactly match the sample map.
2. Choose `normalization=NONE` for raw values or `ROW_ZSCORE` to compare gene patterns.
3. Inspect the tissue bands, sample order, and exported processed matrix.
4. Keep the matrix, sample map, and run manifest for reproducibility.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Expression matrix | yes | empty | Genes in rows and tissues/samples in the header. |
| `sampleMap` Sample map | no | empty | Optional `sample`/`tissue` TSV; samples are grouped and sorted by tissue. |
| `normalization` Value normalization | no | NONE | `NONE` keeps values; `ROW_ZSCORE` standardizes each gene row. |
| `exportData` Export processed data | no | true | Write the reordered matrix beside the plot. |
| `title` Plot title | no | Expression eFP | Chart title. |
| `subtitle` Plot subtitle | no | empty | Optional subtitle. |
| `valueLabel` Value label | no | empty | Optional value-axis label. |
| `width` Image width | no | 1200 | Export width in pixels. |
| `height` Image height | no | 800 | Export height in pixels. |
| `dpi` PDF DPI | no | 144 | PDF export resolution. |
| `exportFormats` Export formats | no | PNG_SVG_PDF | Plot artifact formats. |
| `output` Output prefix | no | empty | Leave blank to save in the run directory. |
| `overwrite` Overwrite outputs | no | false | Allow replacing existing outputs. |

## Read the result

- Output formats: `PNG`, `SVG`, `PDF`, `JSON`.
- Colored bands above the matrix identify tissues; columns are ordered by tissue and then by source order.
- `metrics.sampleGroups` counts distinct tissues; `metrics.genes` and `metrics.samples` describe the displayed matrix.
- The processed TSV preserves the displayed order and values, so it can be used to check the figure.

## Minimal command example

```powershell
opentools run plot.efp --param input=C:\data\expression.tsv --param sampleMap=C:\data\samples.tsv
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged.

## Checks and failure boundaries

- Sample-map files need `sample` and `tissue` headers; duplicate sample rows keep the last tissue.
- Missing or non-numeric matrix cells follow the heatmap defaults (row mean for missing, invalid as missing).
- A sample map with names that do not match the matrix leaves those samples as `Unassigned`.
- Existing outputs are protected by default; enable `overwrite` only after choosing the target prefix.

## Related next steps

- [Heatmap](tool:visual.heatmap)
- [Expression PCA](tool:plot.pca)
- [Expression Correlation](tool:expression.correlation)

[Back to documentation home](opentools-doc:README.md)
