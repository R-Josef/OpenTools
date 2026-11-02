# Heatmap

`visual.heatmap` · Visualization · v1.2.0

## What this tool does

Build a reproducible labeled heatmap with matrix diagnostics, ordering, and headless export.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Turn validated numeric tables into figures for review or publication while retaining a traceable data path.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `TSV`, `CSV`, `TSV.gz`, `CSV.gz`
- Required parameters: `input`
- Confirm that headers, numeric columns, and missing values match the parameters. Review the table before interpreting the figure.

### Input sketch

```text
gene	sample1	sample2	sample3
gene1	12	18	15
gene2	40	36	44
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Input matrix | yes | empty | Select a complete real input file; do not submit the placeholder example path. A labeled numeric matrix with a header row and row-name column. |
| `delimiter` Delimiter | no | AUTO | Prefer the default mode and switch only when the input type or analysis goal requires it. AUTO detects tab, comma, or semicolon. |
| `missingPolicy` Missing values | no | ROW_MEAN | Prefer the default mode and switch only when the input type or analysis goal requires it. Choose whether missing cells stop, impute, zero-fill, or remain gray. |
| `invalidPolicy` Invalid values | no | AS_MISSING | Prefer the default mode and switch only when the input type or analysis goal requires it. Choose whether non-numeric cells stop tool execution or become missing. |
| `normalization` Value normalization | no | NONE | Prefer the default mode and switch only when the input type or analysis goal requires it. Optionally calculate a z-score within each row. |
| `rowOrder` Row order | no | ORIGINAL | Prefer the default mode and switch only when the input type or analysis goal requires it. Use input order, row name, row mean, or deterministic average-linkage clustering. |
| `columnOrder` Column order | no | ORIGINAL | Prefer the default mode and switch only when the input type or analysis goal requires it. Use input order, column name, column mean, or deterministic average-linkage clustering. |
| `palette` Color palette | no | BLUE_WHITE_RED | Prefer the default mode and switch only when the input type or analysis goal requires it. Select the heatmap color ramp. |
| `showValues` Show cell values | no | false | Start with the default and change it only after understanding the explanation. Draw numeric values when cells are large enough. |
| `rangeMode` Color range | no | AUTO | Prefer the default mode and switch only when the input type or analysis goal requires it. AUTO uses the displayed data range; FIXED uses the entered limits. |
| `rangeMin` Fixed minimum | no | -1.0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Lower color-scale limit for FIXED range mode. |
| `rangeMax` Fixed maximum | no | 1.0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Upper color-scale limit for FIXED range mode. |
| `title` Plot title | no | OpenTools Heatmap | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional title written into the plot model. |
| `subtitle` Plot subtitle | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional subtitle written into the plot model. |
| `maxRows` Preview rows | no | 200 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum rows retained in the serialized plot model. |
| `maxColumns` Preview columns | no | 200 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum columns retained in the serialized plot model. |
| `maxClusterItems` Maximum cluster items | no | 1000 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Safety bound for rows or columns participating in hierarchical clustering. |
| `threads` Threads | no | 0 | Keep `0` for automatic selection on large files; use `1` for small inputs or fixed sequential processing. 0 uses AUTO for large matrices; 1 disables parallel distance rows. |
| `width` Image width | no | 1200 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Rendered width in pixels. |
| `height` Image height | no | 800 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Rendered height in pixels. |
| `dpi` PDF DPI | no | 144 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Resolution metadata recorded for the plot export. |
| `exportFormats` Export formats | no | PNG_SVG_PDF | Prefer the default mode and switch only when the input type or analysis goal requires it. Choose PNG, SVG, and PDF artifacts. |
| `exportData` Export processed matrix | no | true | Start with the default and change it only after understanding the explanation. Write the displayed, ordered matrix as TSV. |
| `output` Output prefix | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Optional base path for plot model and image outputs. |
| `overwrite` Overwrite outputs | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing existing custom output files. |

## Read the result

- Output formats: `SVG`, `PNG`, `PDF`, `JSON`
- The heatmap displays a processed matrix; review row/column order, clustering, missing values, and color limits against the exported data.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- If a processed data table is exported, verify that plotted points, groups, ordering, and filters agree with it.

## Minimal command example

```powershell
opentools run visual.heatmap --param input=C:\data\input.tsv
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Empty data, all-missing values, non-numeric columns, or no observations on one side of a threshold can produce an empty or misleading figure.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [eFP Expression](tool:plot.efp)
- [Correlation Heatmap](tool:plot.correlation)

[Back to documentation home](opentools-doc:README.md)
