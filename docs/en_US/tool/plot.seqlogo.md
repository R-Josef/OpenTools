# Sequence Logo

`plot.seqlogo` · Visualization · v1.1.0

## What this tool does

Render a base-frequency logo from aligned FASTA or a position table.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Turn validated numeric tables into figures for review or publication while retaining a traceable data path.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `FASTA`, `FASTA.gz`, `TSV`
- Required parameters: `input`
- Confirm that headers, numeric columns, and missing values match the parameters. Review the table before interpreting the figure.

### Input sketch

**input**

```text
>seq1
ATGCGTACGT
>seq2
ATGCGTTCGT
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` FASTA or position matrix | yes | empty | Select a complete real input file; do not submit the placeholder example path. Aligned nucleotide FASTA or TSV with position, A, C, G, T columns. |
| `inputMode` Input mode | no | AUTO | Prefer the default mode and switch only when the input type or analysis goal requires it. AUTO detects aligned FASTA or the legacy position table. |
| `positionColumn` Position column | no | position | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Position column. |
| `pseudocount` Pseudocount | no | 0.0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Per-base count added before FASTA frequency normalization. |
| `bothStrands` Both strands | no | false | Start with the default and change it only after understanding the explanation. Count each FASTA sequence and its reverse complement. |
| `title` Plot title | no | Sequence Logo | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Chart title. |
| `subtitle` Plot subtitle | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional subtitle. |
| `valueLabel` Value label | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Y-axis value label. |
| `width` Image width | no | 1200 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Export width in pixels. |
| `height` Image height | no | 800 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Export height in pixels. |
| `dpi` PDF DPI | no | 144 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. PDF export resolution. |
| `exportFormats` Export formats | no | PNG_SVG_PDF | Prefer the default mode and switch only when the input type or analysis goal requires it. Plot artifact formats. |
| `output` Output prefix | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite outputs | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing existing outputs. |

## Read the result

- Output formats: `PNG`, `SVG`, `PDF`, `JSON`, `TSV`
- The logo shows positional A/C/G/T preference; verify equal alignment length, pseudocount behavior, and whether reverse complements were counted.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- If a processed data table is exported, verify that plotted points, groups, ordering, and filters agree with it.

## Minimal command example

```powershell
opentools run plot.seqlogo --param input=C:\data\input.fasta
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Empty data, all-missing values, non-numeric columns, or no observations on one side of a threshold can produce an empty or misleading figure.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [MUSCLE Alignment](tool:alignment.muscle)
- [trimAl Alignment Trimming](tool:alignment.trim)

[Back to documentation home](opentools-doc:README.md)
