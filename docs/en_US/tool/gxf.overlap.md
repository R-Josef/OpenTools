# Find GFF3/GTF Overlaps

`gxf.overlap` · Genome annotation · v1.4.0

## What this tool does

Report overlapping annotation intervals with a per-sequence sorted sweep.

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
| `input` Input annotation | yes | empty | Select a complete real input file; do not submit the placeholder example path. GFF3 or GTF. |
| `minOverlap` Minimum overlap | no | 1 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum overlapping bases. |
| `sameTypeOnly` Same type only | no | false | Start with the default and change it only after understanding the explanation. Only compare records with the same feature type. |
| `overlapMode` Overlap mode | no | AUTO | Prefer the default mode and switch only when the input type or analysis goal requires it. AUTO spills large annotations or falls back to disk at maxRecords; DISK always uses a temporary SQLite index. |
| `maxRecords` Maximum records | no | 2000000 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Safety bound for records held by the in-memory overlap path. |
| `maxActiveRecords` Maximum active disk records | no | 2000000 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Safety bound for simultaneously overlapping records retained by the disk sweep. |
| `maxTempMegabytes` Maximum temporary SQLite size | no | 8192 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Disk safety limit in MiB for DISK/AUTO mode; 0 disables the explicit limit. |
| `maxPairs` Maximum pairs | no | 100000 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Cap reported overlap pairs. |
| `output` Overlap report | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing report. |

## Read the result

- Output formats: `TSV`
- The output contains overlapping interval pairs; check minimum overlap, feature type, strand, and report caps so truncation is not mistaken for completion.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run gxf.overlap --param input=C:\data\input.gff3
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
