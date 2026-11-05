# GO Enrichment

`go.enrich` · Enrichment · v1.1.0

## What this tool does

Run offline GO over-representation analysis with hypergeometric tests and BH correction.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Explore expression matrices or functional annotations and produce tables for plotting and review.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `Gene-GO TSV`, `Gene list`
- Required parameters: `genes`
- Confirm that formats, identifiers, headers, and coordinate conventions match the parameter descriptions, and keep the source files.

## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `annotations` Gene-GO annotation | no | empty | Select a complete real input file; do not submit the placeholder example path. Two columns: gene and semicolon/comma-separated GO terms. |
| `resourceId` GO resource pack | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional pack id from resource.manage; its annotation table replaces the file input. |
| `genes` Target genes | yes | empty | Select a complete real input file; do not submit the placeholder example path. One gene ID per line. |
| `background` Background genes | no | empty | Select a complete real input file; do not submit the placeholder example path. Optional one-gene-per-line background list. |
| `pruneOverlap` Prune redundant terms | no | 0.0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Jaccard threshold for overlapping hit sets; 0 disables pruning. |
| `output` Output TSV | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- Results report GO term, hits, background, fold enrichment, P-values, and BH-adjusted values; verify the background and gene-ID mapping first.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run go.enrich --param genes=C:\data\genes.tsv
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Mismatched formats, columns, coordinates, or resource versions fail before outputs are published.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Enrichment Bar Plot](tool:plot.enrichment-bar)
- [Enrichment Bubble Plot](tool:plot.enrichment-bubble)
- [GO Term Levels](tool:go.level)

[Back to documentation home](opentools-doc:README.md)
