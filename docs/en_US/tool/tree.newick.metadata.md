# Newick Node Metadata

`tree.newick.metadata` · Phylogeny · v1.0.0

## What this tool does

Export branch lengths, support labels, depth, and descendant counts for every node.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Run local alignment, multiple-sequence alignment, phylogenetic modeling, or normalize their outputs.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `Newick`
- Required parameters: `input`
- Confirm query and subject molecule types and formats; for external tools, check executable paths, versions, threads, and timeouts.

### Input sketch

**input**

```text
((A:0.1,B:0.2):0.3,C:0.4);
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Input Newick | yes | empty | Select a complete real input file; do not submit the placeholder example path. Newick tree file. |
| `output` Output TSV | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- Each node reports branch length, support, depth, and descendant leaves; non-numeric internal labels remain labels.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run tree.newick.metadata --param input=C:\data\input.nwk
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- External program versions and arguments can change hits, alignments, or trees; record versions and complete commands for publication.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Subset Newick Taxa](tool:tree.newick.subset)
- [IQ-TREE](tool:alignment.iqtree)
- [Newick Summary](tool:tree.newick.summary)

[Back to documentation home](opentools-doc:README.md)
