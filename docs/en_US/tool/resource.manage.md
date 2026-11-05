# Manage GO/KEGG Resource Packs

`resource.manage` · Enrichment · v1.0.0

## What this tool does

List, import, and activate versioned local GO/KEGG resource packs for enrichment tasks.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Explore expression matrices or functional annotations and produce tables for plotting and review.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `GO OBO`, `Gene-GO TSV`, `Gene-pathway TSV`
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
| `action` Action | no | LIST | Prefer the default mode and switch only when the input type or analysis goal requires it. LIST reports packs; IMPORT_GO/IMPORT_KEGG add an immutable pack; ACTIVATE switches the active pack. |
| `kind` List filter | no | ALL | Prefer the default mode and switch only when the input type or analysis goal requires it. Restrict LIST to GO, KEGG, or all packs. |
| `id` Pack id | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional stable id for an import, or the pack to activate. |
| `name` Pack name | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Display name for an imported pack. |
| `version` Pack version | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Version label such as go-basic-2026-11; defaults to the import date. |
| `obo` GO OBO file | no | empty | Select a complete real input file; do not submit the placeholder example path. GO OBO 1.4 file used by go.level and for pack metadata. |
| `annotations` Gene-GO annotation | no | empty | Select a complete real input file; do not submit the placeholder example path. Two columns: gene and GO terms; required for GO enrichment from the pack. |
| `mapping` Gene-pathway mapping | no | empty | Select a complete real input file; do not submit the placeholder example path. Two columns: gene and KEGG pathway IDs. |
| `pathwayNames` Pathway names | no | empty | Select a complete real input file; do not submit the placeholder example path. Optional two-column pathway/name table for KEGG reporting. |
| `activate` Activate after import | no | true | Start with the default and change it only after understanding the explanation. Make the imported pack active for its kind, replacing the previous active pack. |

## Read the result

- Output formats: `TSV`, `JSON`
- Results depend on the selected inputs and parameters; review output files, summary metrics, and diagnostics against the experimental design.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run resource.manage
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Mismatched formats, columns, coordinates, or resource versions fail before outputs are published.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Preranked GSEA](tool:go.gsea)
- [KEGG Enrichment](tool:kegg.enrich)
- [GO Enrichment](tool:go.enrich)

[Back to documentation home](opentools-doc:README.md)
