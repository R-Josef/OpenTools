# Merge or Split Annotation

`gxf.merge-split` · Genome annotation · v1.1.0

## What this tool does

Merge multiple GFF3/GTF files or split one file by sequence ID or feature type.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Inspect or transform genome annotation while validating sequence names, coordinates, and feature relationships.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `GFF3`, `GTF`, `GFF3.gz`, `GTF.gz`
- Required parameters: `inputs`
- Match sequence names to the genome; interpret GFF3/GTF coordinates as 1-based inclusive and BED as 0-based half-open.

### Input sketch

**inputs**

```text
C:\data\part1.gff3
C:\data\part2.gff3
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `mode` Mode | no | MERGE | Prefer the default mode and switch only when the input type or analysis goal requires it. Merge files or split one annotation. |
| `inputs` Input files | yes | empty | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. One GFF3/GTF path per line. SPLIT accepts exactly one path. |
| `output` Merged output | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Required for MERGE mode. |
| `outputDirectory` Split output directory | no | empty string | Leave optional values blank first; when provided, keep IDs, column names, or syntax consistent with the input. Required for SPLIT mode. The directory is created when absent. |
| `splitBy` Split by | no | SEQ_ID | Prefer the default mode and switch only when the input type or analysis goal requires it. Partition key for SPLIT mode. |
| `maxRecords` Maximum split records | no | 2000000 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Safety bound for records buffered in SPLIT mode. |
| `overwrite` Overwrite outputs | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing existing outputs. |

## Read the result

- Output formats: `GFF3`, `GTF`, `TSV`
- Merged output preserves input records and may contain duplicate IDs; split output creates files by sequence or type, so inspect naming and directories.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run gxf.merge-split --param inputs=C:\data\input1.txt
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Check unknown sequences, reversed coordinates, out-of-range positions, and missing parent-child features before downstream interpretation.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Check Genome and Annotation Match](tool:gxf.genome.match)
- [Convert BED to GFF3](tool:bed.to-gff)
- [Gene Density Windows](tool:gxf.gene-density)

[Back to documentation home](opentools-doc:README.md)
