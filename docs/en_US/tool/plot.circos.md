# Circos Overview

`plot.circos` · Visualization · v1.1.0

## What this tool does

Render a circular chromosome ideogram with feature rings and labels.

Each sequence becomes an arc proportional to its largest feature coordinate; features are drawn as colored ticks inside the ring and grouped by their label. The center reports sequence and feature counts.

## When to use it

- Get a genome-wide overview of chromosomes and feature distribution.
- Show gene, TE, or annotation density by sequence and feature class.
- Produce a reproducible circular figure for reports before moving to a full Circos configuration.

## Prepare the input

- Declared formats: `TSV`, `CSV`
- Required columns: `seq_id`, `start`, `end`; `label` is optional.
- Coordinates are 1-based, start ≤ end, in base pairs.
- A sequence's arc scales to the largest end coordinate seen for that sequence; a feature with no explicit chromosome length sets the scale.

### Input sketch

```text
seq_id	start	end	label
chr1	1	2000000	gene-rich
chr1	2100000	2400000	repeat-rich
chr2	1	1500000	gene-rich
```

## Recommended workflow

1. Confirm sequence IDs, coordinate convention, and that start ≤ end.
2. Use a feature table with one row per interval; a missing label becomes `feature`.
3. Run with a square canvas (for example 800×800) so the circle stays readable.
4. Inspect sequence labels, ring proportions, and feature colors before publication.
5. Keep the table, parameters, and run manifest for reproducibility.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Feature table | yes | empty | TSV/CSV with sequence, start, end, and optional label. |
| `sequenceColumn` Sequence column | no | seq_id | Sequence identifier column. |
| `startColumn` Start column | no | start | Feature start column (1-based). |
| `endColumn` End column | no | end | Feature end column (1-based, ≥ start). |
| `labelColumn` Label column | no | label | Feature class column; missing values become `feature`. |
| `title` Plot title | no | Circos Overview | Chart title. |
| `subtitle` Plot subtitle | no | empty | Optional subtitle. |
| `valueLabel` Value label | no | empty | Optional value-axis label. |
| `width` Image width | no | 1200 | Export width in pixels; a square canvas works best. |
| `height` Image height | no | 800 | Export height in pixels. |
| `dpi` PDF DPI | no | 144 | PDF export resolution. |
| `exportFormats` Export formats | no | PNG_SVG_PDF | Plot artifact formats. |
| `output` Output prefix | no | empty | Leave blank to save in the run directory. |
| `overwrite` Overwrite outputs | no | false | Allow replacing existing outputs. |

## Read the result

- Output formats: `PNG`, `SVG`, `PDF`, `JSON`.
- The outer ring is one sequence; ring span is proportional to the largest coordinate in that sequence.
- Inner ticks are features, colored by label; sequence labels sit outside the ring.
- The center reports the number of sequences and features.
- Use the source table for exact coordinates; the plot is an overview, not a coordinate readout.

## Minimal command example

```powershell
opentools run plot.circos --param input=C:\data\features.tsv --param width=800 --param height=800
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged.

## Checks and failure boundaries

- Empty tables, non-numeric coordinates, or start > end are rejected or skipped; check the input first.
- A sequence with no explicit chromosome length uses its largest feature end, so sparse tables can exaggerate small chromosomes.
- Many tiny features become dense ticks; aggregate intervals before plotting when the ring is unreadable.
- Very long labels can overlap; use shorter feature classes or a larger canvas.
- Existing outputs are protected by default.

## Related next steps

- [Gene Location](tool:plot.gene-location)
- [Dot Plot](tool:plot.dotplot)
- [Collinearity Plot](tool:plot.collinearity)

[Back to documentation home](opentools-doc:README.md)
