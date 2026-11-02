# GO Term Levels

`go.level` · Enrichment · v1.1.0

## What this tool does

Calculate shortest root distance for terms in a local GO parent hierarchy.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Explore expression matrices or functional annotations and produce tables for plotting and review.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `Term-parent TSV`, `GO info TSV`, `OBO 1.4`
- Required parameters: `hierarchy`
- Confirm that formats, identifiers, headers, and coordinate conventions match the parameter descriptions, and keep the source files.

## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `hierarchy` GO hierarchy | yes | empty | Select a complete real input file; do not submit the placeholder example path. Two-column term/parent TSV or OBO 1.4 with [Term] stanzas; TSV roots use . or a blank parent. |
| `termInfo` Term information | no | empty | Select a complete real input file; do not submit the placeholder example path. Optional term/name/namespace table. |
| `output` Output TSV | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- The output gives each GO term's shortest distance from the root; deeper terms tend to be more specific, but depth is not a quality or significance score.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run go.level --param hierarchy=C:\data\hierarchy.tsv
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Mismatched formats, columns, coordinates, or resource versions fail before outputs are published.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [KEGG Enrichment](tool:kegg.enrich)
- [GO Enrichment](tool:go.enrich)

[Back to documentation home](opentools-doc:README.md)
