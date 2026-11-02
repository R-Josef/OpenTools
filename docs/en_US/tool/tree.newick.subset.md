# Subset Newick Taxa

`tree.newick.subset` · Phylogeny · v1.0.0

## What this tool does

Retain selected taxa and prune empty branches.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Run local alignment, multiple-sequence alignment, phylogenetic modeling, or normalize their outputs.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `Newick`
- Required parameters: `input`, `taxa`
- Confirm query and subject molecule types and formats; for external tools, check executable paths, versions, threads, and timeouts.

### Input sketch

**input**

```text
((A:0.1,B:0.2):0.3,C:0.4);
```


**taxa**

```text
sampleA
sampleB
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
| `taxa` Taxa | yes | empty | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. One retained leaf name per line. |
| `output` Output Newick | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `Newick`
- Selected taxa are retained and empty branches pruned; check missing taxa, rooting, and branch lengths.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run tree.newick.subset --param input=C:\data\input.nwk --param taxa=sampleA
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- External program versions and arguments can change hits, alignments, or trees; record versions and complete commands for publication.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Tree Render](tool:tree.render)
- [Newick Summary](tool:tree.newick.summary)

[Back to documentation home](opentools-doc:README.md)
