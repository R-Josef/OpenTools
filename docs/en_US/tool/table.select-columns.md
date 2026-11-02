# Select Table Columns

`table.select-columns` · Table · v1.2.0

## What this tool does

Select and reorder columns in a table.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Organize experimental, annotation, or statistical tables so their headers and rows fit the next analysis.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `TSV`, `CSV`
- Required parameters: `input`, `columns`
- Confirm that the first row is a unique header matching the parameters and that CSV quoting and delimiters are valid.

### Input sketch

**input**

```text
group	value
control	1.2
treatment	2.4
```

## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Input table | yes | empty | Select a complete real input file; do not submit the placeholder example path. Delimited text with header. |
| `columns` Columns | yes | empty | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Comma/whitespace-separated output column names. |
| `threads` Threads | no | 0 | Keep `0` for automatic selection on large files; use `1` for small inputs or fixed sequential processing. 0 keeps sequential parsing; explicit 2-64 enables bounded workers. |
| `delimiter` Delimiter | no | AUTO | Prefer the default mode and switch only when the input type or analysis goal requires it. Input table delimiter. |
| `output` Output table | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- Output columns follow the requested names and order; missing or duplicate names and empty headers fail validation.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.

## Minimal command example

```powershell
opentools run table.select-columns --param input=C:\data\input.tsv --param columns=id
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Duplicate headers, missing headers, empty keys, and non-numeric values cause validation or execution failures.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Preview Delimited Table](tool:table.preview)
- [Transpose Table](tool:table.transpose)
- [Deduplicate Table](tool:table.deduplicate)

[Back to documentation home](opentools-doc:README.md)
