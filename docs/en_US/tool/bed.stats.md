# BED Statistics

`bed.stats` · Genome annotation · v1.1.0

## What this tool does

Summarize BED interval count, span, length, strands, and sequence IDs.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Inspect or transform genome annotation while validating sequence names, coordinates, and feature relationships.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `BED`, `BED.gz`
- Required parameters: `input`
- Match sequence names to the genome; interpret GFF3/GTF coordinates as 1-based inclusive and BED as 0-based half-open.

### Input sketch

**input**

```text
chr1	0	100	feature1	0	+
chr1	50	150	feature2	0	-
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Input BED | yes | empty | Select a complete real input file; do not submit the placeholder example path. BED3 or BED3+, optionally gzip-compressed. |
| `threads` Threads | no | 0 | Keep `0` for automatic selection on large files; use `1` for small inputs or fixed sequential processing. 0 uses AUTO for large files; 1 disables parallel record processing. |
| `output` Output TSV | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- The summary reports interval count, span, length distribution, strands, and sequence IDs, helping expose empty intervals, unusual lengths, and naming errors.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.

## Minimal command example

```powershell
opentools run bed.stats --param input=C:\data\input.bed
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Check unknown sequences, reversed coordinates, out-of-range positions, and missing parent-child features before downstream interpretation.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Convert BED to GFF3](tool:bed.to-gff)
- [BED Mean Coverage](tool:bed.coverage)
- [Inspect GFF3/GTF](tool:gxf.inspect)

[Back to documentation home](opentools-doc:README.md)
