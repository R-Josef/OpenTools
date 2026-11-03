# Inspect GFF3/GTF

`gxf.inspect` · Genome annotation · v1.1.0

## What this tool does

Summarize annotation records, sequence IDs, and feature types.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Inspect or transform genome annotation while validating sequence names, coordinates, and feature relationships.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `GFF3`, `GTF`, `GFF3.gz`, `GTF.gz`
- Required parameters: `input`
- Match sequence names to the genome; interpret GFF3/GTF coordinates as 1-based inclusive and BED as 0-based half-open.

## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Input GFF3/GTF | yes | empty | Select a complete real input file; do not submit the placeholder example path. Annotation file to inspect. |
| `threads` Threads | no | 0 | Keep `0` for automatic selection on large files; use `1` for small inputs or fixed sequential processing. 0 uses AUTO for large files; 1 disables parallel record processing. |
| `skipInvalidLines` Skip invalid lines | no | true | Keep `true` for dirty annotations and review the skipped line numbers; set `false` to fail fast. Record malformed lines in the summary instead of failing the whole inspection. |
| `output` Output prefix | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Optional TSV file. A companion feature-type output is written beside it. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing existing custom output files. |

## Read the result

- Output formats: `TSV`
- The summary aggregates records, sequences, and feature types; look first for unknown sequences, extreme lengths, and unexpected feature classes.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.

## Minimal command example

```powershell
opentools run gxf.inspect --param input=C:\data\input.tsv
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Check unknown sequences, reversed coordinates, out-of-range positions, and missing parent-child features before downstream interpretation.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Check and Repair Annotation](tool:gxf.check-repair)
- [Check Genome and Annotation Match](tool:gxf.genome.match)
- [Convert GFF3/GTF to BED](tool:gxf.to-bed)

[Back to documentation home](opentools-doc:README.md)
