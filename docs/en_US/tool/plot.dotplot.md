# Dot Plot

`plot.dotplot` · Visualization · v1.1.0

## What this tool does

Plot numeric coordinates or BLAST outfmt 6 HSPs as a genome-position dot plot.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Turn validated numeric tables into figures for review or publication while retaining a traceable data path.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `TSV`, `CSV`
- Required parameters: `input`
- Confirm that headers, numeric columns, and missing values match the parameters. Review the table before interpreting the figure.

### Input sketch

```text
x	y	group
1.0	2.1	control
1.8	3.0	control
2.2	4.4	treatment
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Dot plot table | yes | empty | Select a complete real input file; do not submit the placeholder example path. Delimited table with x/y columns, or BLAST outfmt 6 with qseqid, sseqid, pident, qstart, qend, sstart, and send. |
| `inputFormat` Input format | no | AUTO | AUTO detects BLAST outfmt 6 from its qseqid and sseqid headers; choose the explicit mode when headers are ambiguous. |
| `queryLengthColumn` Query length column | no | qlen | Optional qlen column for BLAST input; derived from qstart/qend when absent. |
| `subjectLengthColumn` Subject length column | no | slen | Optional slen column for BLAST input; derived from sstart/send when absent. |
| `maxHits` Maximum HSPs | no | 5000 | Maximum BLAST HSP rows to draw; lower it if a plot becomes too dense. |
| `xColumn` X column | no | x | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Numeric x column. |
| `yColumn` Y column | no | y | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Numeric y column. |
| `groupColumn` Group column | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional series grouping column. |
| `title` Plot title | no | Dot Plot | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Chart title. |
| `subtitle` Plot subtitle | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional subtitle. |
| `valueLabel` Value label | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Y-axis value label. |
| `width` Image width | no | 1200 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Export width in pixels. |
| `height` Image height | no | 800 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Export height in pixels. |
| `dpi` PDF DPI | no | 144 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. PDF export resolution. |
| `exportFormats` Export formats | no | PNG_SVG_PDF | Prefer the default mode and switch only when the input type or analysis goal requires it. Plot artifact formats. |
| `output` Output prefix | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite outputs | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing existing outputs. |

## Read the result

- Output formats: `PNG`, `SVG`, `PDF`, `JSON`
- In table mode, each point is an x/y numeric pair; confirm units and grouping before interpreting trends or outliers.
- In BLAST mode, each segment connects an HSP start and end in query/subject coordinates; colors group hits by identity.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- If a processed data table is exported, verify that plotted points, groups, ordering, and filters agree with it.

## Minimal command example

```powershell
opentools run plot.dotplot --param input=C:\data\input.tsv
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Empty data, all-missing values, non-numeric columns, or no observations on one side of a threshold can produce an empty or misleading figure.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Filter Table](tool:table.filter)
- [Preview Delimited Table](tool:table.preview)

[Back to documentation home](opentools-doc:README.md)
