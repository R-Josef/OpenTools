# Filter GFF3/GTF

`gxf.filter` · Genome annotation · v1.1.0

## What this tool does

Filter annotation records by type, source, sequence, strand, coordinates, length, and attributes.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Inspect or transform genome annotation while validating sequence names, coordinates, and feature relationships.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `GFF3`, `GTF`, `GFF3.gz`, `GTF.gz`
- Required parameters: `input`
- Match sequence names to the genome; interpret GFF3/GTF coordinates as 1-based inclusive and BED as 0-based half-open.

### Input sketch

**input**

```text
##gff-version 3
chr1	OpenTools	gene	1	100	.	+	.	ID=gene1
chr1	OpenTools	mRNA	1	100	.	+	.	ID=tx1;Parent=gene1
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Input annotation | yes | empty | Select a complete real input file; do not submit the placeholder example path. GFF3 or GTF, optionally gzip-compressed. |
| `threads` Threads | no | 0 | Keep `0` for automatic selection on large files; use `1` for small inputs or fixed sequential processing. 0 uses AUTO for large files; 1 disables parallel record processing. |
| `types` Feature types | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Feature types separated by whitespace, commas, or semicolons. |
| `sources` Sources | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Source values to retain. |
| `seqIds` Sequence IDs | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Sequence IDs to retain. |
| `strand` Strand | no | ANY | Prefer the default mode and switch only when the input type or analysis goal requires it. Required annotation strand. |
| `minStart` Minimum start | no | 0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum record start, inclusive. |
| `maxEnd` Maximum end | no | 2147483647 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum record end, inclusive. |
| `minLength` Minimum length | no | 0 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum record length, inclusive. |
| `maxLength` Maximum length | no | 2147483647 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Maximum record length, inclusive. |
| `attributeKey` Attribute key | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional attribute key to test. Blank skips attribute filtering. |
| `attributeRegex` Attribute regex | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Optional Java regular expression for any value of the selected attribute. |
| `ignoreCase` Ignore case | no | false | Start with the default and change it only after understanding the explanation. Case-insensitive type, source, and sequence matching. |
| `output` Output annotation | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `GFF3`, `GTF`, `GFF3.gz`, `GTF.gz`
- Records passing type, source, sequence, strand, coordinate, and attribute filters are retained; filtered annotation may lose parent-child context.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.

## Minimal command example

```powershell
opentools run gxf.filter --param input=C:\data\input.gff3
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Check unknown sequences, reversed coordinates, out-of-range positions, and missing parent-child features before downstream interpretation.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Check Genome and Annotation Match](tool:gxf.genome.match)
- [Gene Density Windows](tool:gxf.gene-density)
- [BED12 Block Analysis](tool:bed.blocks)

[Back to documentation home](opentools-doc:README.md)
