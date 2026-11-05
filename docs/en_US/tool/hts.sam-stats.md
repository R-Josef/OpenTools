# SAM/BAM/CRAM Statistics

`hts.sam-stats` · HTS · v1.0.0

## What this tool does

Stream local alignment statistics with HTSJDK.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Summarize or filter SAM/BAM/CRAM, BED, and VCF/BCF data with a traceable QC record.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `SAM`, `BAM`, `CRAM`
- Required parameters: `input`
- CRAM may also need a reference FASTA. Indexed BAM/CRAM is preferable for region access, and VCF/BCF should retain sorted records and a valid header.

### Input sketch

**input**

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
| `input` Input alignment | yes | empty | Select a complete real input file; do not submit the placeholder example path. SAM, BAM, or CRAM. |
| `reference` Reference FASTA | no | empty | Select a complete real input file; do not submit the placeholder example path. Required for CRAM when the reference is not embedded. |
| `output` Output TSV | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- The summary supports checks of alignment rate, read count, length, and references; interpret it with sorting and duplicate/mapq filtering.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run hts.sam-stats --param input=C:\data\input.bam
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
