# Prepare Differential Expression Input

`expression.differential-input` · Expression · v1.0.0

## What this tool does

Combine a count matrix and sample groups into a tidy long table.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Explore expression matrices or functional annotations and produce tables for plotting and review.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `Count matrix`, `Sample groups`
- Required parameters: `input`, `groups`
- Confirm that formats, identifiers, headers, and coordinate conventions match the parameter descriptions, and keep the source files.

### Input sketch

```text
gene	sample1	sample2	sample3
gene1	12	18	15
gene2	40	36	44
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Count matrix | yes | empty | Select a complete real input file; do not submit the placeholder example path. Genes in the first column and samples in the header. |
| `groups` Sample groups | yes | empty | Select a complete real input file; do not submit the placeholder example path. Two-column sample/group file. |
| `delimiter` Delimiter | no | AUTO | Prefer the default mode and switch only when the input type or analysis goal requires it. Matrix delimiter. |
| `log2` Log2 transform counts | no | false | Start with the default and change it only after understanding the explanation. Write log2(x+1) values. |
| `output` Output long table | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- The output is a long table for differential analysis; verify that sample columns match the grouping file and no group assignment is missing.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run expression.differential-input --param input=C:\data\input.bam --param groups=C:\data\groups.bam
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Mismatched formats, columns, coordinates, or resource versions fail before outputs are published.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Volcano Plot](tool:plot.volcano)
- [Normalize Expression](tool:expression.normalize)

[Back to documentation home](opentools-doc:README.md)
