# Import eggNOG Annotations

`annotation.eggnog.import` · Annotation · v1.0.0

## What this tool does

Normalize eggNOG-mapper TSV annotations and summarize GO, KEGG, COG, and descriptions.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Normalize external annotation output into stable columns for filtering, joining, and visualization.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `eggNOG TSV`, `TSV.gz`
- Required parameters: `input`
- Confirm that formats, identifiers, headers, and coordinate conventions match the parameter descriptions, and keep the source files.

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
| `input` Input eggNOG table | yes | empty | Select a complete real input file; do not submit the placeholder example path. eggNOG-mapper annotation TSV with header. |
| `output` Output TSV | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- The normalized table preserves gene, GO, KEGG, COG, and description fields; verify eggNOG database version and gene-ID mapping.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run annotation.eggnog.import --param input=C:\data\input.tsv
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Mismatched formats, columns, coordinates, or resource versions fail before outputs are published.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [GO Enrichment](tool:go.enrich)
- [KEGG Enrichment](tool:kegg.enrich)
- [Filter Table](tool:table.filter)

[Back to documentation home](opentools-doc:README.md)
