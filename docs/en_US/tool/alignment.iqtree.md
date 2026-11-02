# IQ-TREE

`alignment.iqtree` · Phylogeny · v1.0.0

## What this tool does

Run IQ-TREE maximum-likelihood phylogeny on a local alignment.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Run local alignment, multiple-sequence alignment, phylogenetic modeling, or normalize their outputs.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `FASTA alignment`
- Required parameters: `input`
- Confirm query and subject molecule types and formats; for external tools, check executable paths, versions, threads, and timeouts.

### Input sketch

**input**

```text
>seq1
ATGCGTACGT
>seq2
ATGCGTTCGT
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Input alignment | yes | empty | Select a complete real input file; do not submit the placeholder example path. Multiple sequence alignment. |
| `model` Substitution model | no | MFP | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. IQ-TREE model or MFP. |
| `threads` Threads | no | 4 | The default is aligned with the external program; increase it only when CPU, memory, and scheduling allow. Worker threads. |
| `bootstrap` Ultrafast bootstrap | no | false | Start with the default and change it only after understanding the explanation. Run 1000 ultrafast bootstrap replicates. |
| `timeoutMinutes` Timeout minutes | no | 240 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum runtime. |
| `executablePath` IQ-TREE executable | no | empty | Select a complete real input file; do not submit the placeholder example path. Optional explicit executable. |
| `output` Output tree | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing tree. |

## Read the result

- Output formats: `Newick`
- The Newick tree reports topology and branch information; when enabled, interpret branches together with bootstrap support.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.

## Minimal command example

```powershell
opentools run alignment.iqtree --param input=C:\data\input.fasta
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- External program versions and arguments can change hits, alignments, or trees; record versions and complete commands for publication.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Newick Summary](tool:tree.newick.summary)
- [Tree Render](tool:tree.render)
- [Subset Newick Taxa](tool:tree.newick.subset)

[Back to documentation home](opentools-doc:README.md)
