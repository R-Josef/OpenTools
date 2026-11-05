# Differential Expression Test

`expression.differential` · Expression · v1.0.0

## What this tool does

Compare two sample groups with Welch t-test or Mann-Whitney U and BH-adjusted p-values.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Explore expression matrices or functional annotations and produce tables for plotting and review.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `Expression matrix`, `Sample groups`
- Required parameters: `input`, `groups`
- Confirm that formats, identifiers, headers, and coordinate conventions match the parameter descriptions, and keep the source files.

### Input sketch

**input**

```text
@HD	VN:1.6	SO:coordinate
@SQ	SN:chr1	LN:1000
read1	0	chr1	10	60	8M	*	0	0	ACGTACGT	IIIIIIII
```


**groups**

```text
@HD	VN:1.6	SO:coordinate
@SQ	SN:chr1	LN:1000
read1	0	chr1	10	60	8M	*	0	0	ACGTACGT	IIIIIIII
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Expression matrix | yes | empty | Select a complete real input file; do not submit the placeholder example path. Genes in the first column and samples in the header. |
| `groups` Sample groups | yes | empty | Select a complete real input file; do not submit the placeholder example path. Two-column sample/group file with exactly two groups. |
| `test` Statistical test | no | WELCH_T | Prefer the default mode and switch only when the input type or analysis goal requires it. Two-group test to run. |
| `delimiter` Delimiter | no | AUTO | Prefer the default mode and switch only when the input type or analysis goal requires it. Matrix delimiter. |
| `log2` Log2 transform values | no | false | Start with the default and change it only after understanding the explanation. Apply log2(x+1) before testing. |
| `adjustment` P-value adjustment | no | BH | Prefer the default mode and switch only when the input type or analysis goal requires it. Multiple-testing correction. |
| `output` Output TSV | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- Each gene reports group means, log2 fold change, test statistic, raw p-value, and adjusted p-value; each group needs at least two replicates.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run expression.differential --param input=C:\data\input.bam --param groups=C:\data\groups.bam
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Mismatched formats, columns, coordinates, or resource versions fail before outputs are published.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Prepare Differential Expression Input](tool:expression.differential-input)
- [Expression PCA](tool:expression.pca)
- [Normalize Expression](tool:expression.normalize)

[Back to documentation home](opentools-doc:README.md)
