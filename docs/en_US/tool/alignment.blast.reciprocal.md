# Reciprocal Best BLAST Hits

`alignment.blast.reciprocal` · Alignment · v1.0.0

## What this tool does

Combine forward and reverse BLAST tables and retain mutual best-hit pairs.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Run local alignment, multiple-sequence alignment, phylogenetic modeling, or normalize their outputs.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `BLAST tabular`
- Required parameters: `forward`, `reverse`
- Confirm query and subject molecule types and formats; for external tools, check executable paths, versions, threads, and timeouts.

## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `forward` Forward BLAST table | yes | empty | Select a complete real input file; do not submit the placeholder example path. Query-to-subject BLAST outfmt 6 output. |
| `reverse` Reverse BLAST table | yes | empty | Select a complete real input file; do not submit the placeholder example path. Subject-to-query BLAST outfmt 6 output. |
| `output` Output table | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- Only mutual best-hit pairs from forward and reverse searches are retained; verify best-hit rules and query orientation.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run alignment.blast.reciprocal --param forward=C:\data\forward.tsv --param reverse=C:\data\reverse.tsv
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- External program versions and arguments can change hits, alignments, or trees; record versions and complete commands for publication.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [trimAl Alignment Trimming](tool:alignment.trim)
- [DIAMOND Protein Alignment](tool:alignment.diamond)
- [Local BLAST](tool:alignment.blast.local)

[Back to documentation home](opentools-doc:README.md)
