# Venn / UpSet

`visual.venn` · Visualization · v1.0.0

## What this tool does

Calculate exact set intersections and render a Venn diagram or UpSet plot.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Turn validated numeric tables into figures for review or publication while retaining a traceable data path.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `TSV`, `CSV`, `TSV.gz`, `CSV.gz`
- Required parameters: `input`
- Confirm that headers, numeric columns, and missing values match the parameters. Review the table before interpreting the figure.

### Input sketch

**input**

```text
group	value
control	1.2
treatment	2.4
```

## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Set table | yes | empty | Select a complete real input file; do not submit the placeholder example path. A delimited table with one set per column and one member per row. |
| `delimiter` Delimiter | no | AUTO | Prefer the default mode and switch only when the input type or analysis goal requires it. AUTO detects tab, comma, or semicolon. |
| `displayMode` Display mode | no | AUTO | Prefer the default mode and switch only when the input type or analysis goal requires it. AUTO uses Venn for up to three sets and UpSet otherwise. |
| `maximumIntersections` Maximum intersections | no | 20 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum intersections drawn in UpSet mode. |
| `title` Plot title | no | Set intersections | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional title written into the plot model. |
| `subtitle` Plot subtitle | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional subtitle written into the plot model. |
| `width` Image width | no | 1100 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Rendered width in pixels. |
| `height` Image height | no | 760 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Rendered height in pixels. |
| `dpi` PDF DPI | no | 144 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Resolution metadata recorded for the plot export. |
| `exportFormats` Export formats | no | PNG_SVG_PDF | Prefer the default mode and switch only when the input type or analysis goal requires it. Choose PNG, SVG, and PDF artifacts. |
| `exportData` Export intersections | no | true | Start with the default and change it only after understanding the explanation. Write exact intersection counts and examples as TSV. |
| `output` Output prefix | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Optional base path for plot model and image outputs. |
| `overwrite` Overwrite outputs | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing existing custom output files. |

## Read the result

- Output formats: `SVG`, `PNG`, `PDF`, `JSON`
- Exact intersection counts are exported when `exportData=true`; Venn is compact for a few sets, while UpSet scales better for many sets.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- If a processed data table is exported, verify that plotted points, groups, ordering, and filters agree with it.

## Minimal command example

```powershell
opentools run visual.venn --param input=C:\data\input.tsv
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Empty data, all-missing values, non-numeric columns, or no observations on one side of a threshold can produce an empty or misleading figure.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Filter Table](tool:table.filter)
- [Select Table Columns](tool:table.select-columns)

[Back to documentation home](opentools-doc:README.md)
