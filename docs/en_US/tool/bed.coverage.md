# BED Mean Coverage

`bed.coverage` · HTS · v1.3.0

## What this tool does

Calculate interval-mean depth without per-base arrays, using BAM/CRAM indexes when available.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Summarize or filter SAM/BAM/CRAM, BED, and VCF/BCF data with a traceable QC record.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `BED`, `SAM`, `BAM`, `CRAM`
- Required parameters: `alignment`, `regions`
- CRAM may also need a reference FASTA. Indexed BAM/CRAM is preferable for region access, and VCF/BCF should retain sorted records and a valid header.

### Input sketch

**alignment**

```text
@HD	VN:1.6	SO:coordinate
@SQ	SN:chr1	LN:1000
read1	0	chr1	10	60	8M	*	0	0	ACGTACGT	IIIIIIII
```


**regions**

```text
chr1	0	100	feature1	0	+
chr1	50	150	feature2	0	-
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `alignment` Alignment | yes | empty | Select a complete real input file; do not submit the placeholder example path. SAM, BAM, or CRAM. |
| `reference` Reference FASTA | no | empty | Select a complete real input file; do not submit the placeholder example path. Required for CRAM when the reference is not embedded. |
| `regions` BED regions | yes | empty | Select a complete real input file; do not submit the placeholder example path. Intervals to summarize. |
| `threads` Threads | no | 0 | Keep `0` for automatic selection on large files; use `1` for small inputs or fixed sequential processing. 0 uses AUTO for large indexed alignments; 1 disables parallel regions. |
| `countOverlappingPairsOnce` Count overlapping pairs once | no | false | Start with the default and change it only after understanding the explanation. Use QNAME-based overlap deduplication for mapped read pairs; default interval events remain faster. |
| `output` Output TSV | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- The table reports mean depth for each BED interval; interpret it together with interval size, read distribution, and the capture or sequencing strategy.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.

## Minimal command example

```powershell
opentools run bed.coverage --param alignment=C:\data\alignment.bam --param regions=C:\data\regions.bed
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Confirm sorted order, indexes, reference build, and sequence dictionary; unsorted or mismatched inputs can fail or mislead.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [BAM Coverage](tool:hts.bam.coverage)
- [VCF Statistics](tool:hts.vcf.stats)
- [Filter VCF](tool:hts.vcf.filter)

[Back to documentation home](opentools-doc:README.md)
