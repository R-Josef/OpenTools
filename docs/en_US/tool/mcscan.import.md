# Import MCScanX Collinearity

`mcscan.import` · Comparative genomics · v1.0.0

## What this tool does

Parse MCScanX collinearity blocks into a tidy TSV.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Prepare, run, or organize comparative-genomics analyses such as collinearity and Ka/Ks.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `collinearity`
- Required parameters: `input`
- Confirm that formats, identifiers, headers, and coordinate conventions match the parameter descriptions, and keep the source files.

## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Input collinearity | yes | empty | Select a complete real input file; do not submit the placeholder example path. MCScanX .collinearity file. |
| `output` Output TSV | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- Each row is a collinear gene pair with block and order fields; missing or out-of-order rows may indicate an input format problem.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run mcscan.import --param input=C:\data\input.tsv
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Mismatched formats, columns, coordinates, or resource versions fail before outputs are published.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Collinearity Plot](tool:plot.collinearity)
- [Filter Table](tool:table.filter)

[Back to documentation home](opentools-doc:README.md)
