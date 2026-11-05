# Preranked GSEA

`go.gsea` · Enrichment · v1.0.0

## What this tool does

Run preranked gene-set enrichment with weighted running-sum scores and permutation p-values.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Explore expression matrices or functional annotations and produce tables for plotting and review.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `Ranked genes`, `GMT gene sets`
- Required parameters: `ranking`, `geneSets`
- Confirm that formats, identifiers, headers, and coordinate conventions match the parameter descriptions, and keep the source files.

## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `ranking` Ranked genes | yes | empty | Select a complete real input file; do not submit the placeholder example path. Two columns: gene and numeric score, optionally with a header. |
| `geneSets` GMT gene sets | yes | empty | Select a complete real input file; do not submit the placeholder example path. GMT file: set ID, description, then one or more genes. |
| `minSetSize` Minimum set size | no | 15 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Skip sets with fewer ranked genes. |
| `maxSetSize` Maximum set size | no | 500 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Skip sets with more ranked genes. |
| `permutations` Permutations | no | 1000 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Deterministic gene-score permutations for p-values. |
| `seed` Random seed | no | 13 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Seed for reproducible permutations. |
| `output` Output TSV | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- NES and permutation p-values depend on ranking direction and set size; check GMT identifiers against the ranking and reproduce with a fixed seed.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run go.gsea --param ranking=C:\data\ranking.tsv --param geneSets=C:\data\geneSets.tsv
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Mismatched formats, columns, coordinates, or resource versions fail before outputs are published.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [KEGG Enrichment](tool:kegg.enrich)
- [Manage GO/KEGG Resource Packs](tool:resource.manage)
- [GO Enrichment](tool:go.enrich)

[Back to documentation home](opentools-doc:README.md)
