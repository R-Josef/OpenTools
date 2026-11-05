# BLAST Alignment Viewer Data

`alignment.blast.alignments` · Alignment · v1.0.0

## What this tool does

Extract paired query/subject alignments, coordinates, E-values, and midlines from BLAST XML.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Run local alignment, multiple-sequence alignment, phylogenetic modeling, or normalize their outputs.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `BLAST XML`, `XML.gz`
- Required parameters: `input`
- Confirm query and subject molecule types and formats; for external tools, check executable paths, versions, threads, and timeouts.

## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Input BLAST XML | yes | empty | Select a complete real input file; do not submit the placeholder example path. BLAST XML output. |
| `output` Output TSV | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- Each HSP reports query/subject coordinates and paired alignment strings; verify qseq/hseq completeness, midline, and coordinate orientation.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run alignment.blast.alignments --param input=C:\data\input.tsv
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- External program versions and arguments can change hits, alignments, or trees; record versions and complete commands for publication.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [trimAl Alignment Trimming](tool:alignment.trim)
- [Reciprocal Best BLAST Hits](tool:alignment.blast.reciprocal)
- [HMMER hmmsearch](tool:alignment.hmmer.hmmsearch)

[Back to documentation home](opentools-doc:README.md)
