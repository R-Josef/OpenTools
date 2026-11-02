# Run MCScanX

`mcscan.run` · Comparative genomics · v1.0.0

## What this tool does

Run local MCScanX on prepared .gff and .blast files.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Prepare, run, or organize comparative-genomics analyses such as collinearity and Ka/Ks.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `MCScanX inputs`
- No parameter is marked required; still confirm the dataset, paths, and runtime environment.
- Confirm that formats, identifiers, headers, and coordinate conventions match the parameter descriptions, and keep the source files.

## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `prefix` Input prefix | no | mcscan | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Prefix of matching .gff and .blast files. |
| `timeoutMinutes` Timeout minutes | no | 60 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum runtime. |
| `executablePath` MCScanX executable | no | empty | Select a complete real input file; do not submit the placeholder example path. Optional explicit executable. |
| `output` Output collinearity | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `collinearity`
- The output is MCScanX collinearity; check the paired `.gff`/`.blast` files, program version, memory, timeout, and blocks.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run mcscan.run
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Mismatched formats, columns, coordinates, or resource versions fail before outputs are published.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Import MCScanX Collinearity](tool:mcscan.import)
- [Collinearity Plot](tool:plot.collinearity)

[Back to documentation home](opentools-doc:README.md)
