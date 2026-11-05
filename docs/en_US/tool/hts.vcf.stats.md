# VCF Statistics

`hts.vcf.stats` · HTS · v1.1.0

## What this tool does

Summarize variants, samples, SNPs, indels, transitions, and transversions.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Summarize or filter SAM/BAM/CRAM, BED, and VCF/BCF data with a traceable QC record.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `VCF`, `VCF.gz`, `BCF`
- Required parameters: `input`
- CRAM may also need a reference FASTA. Indexed BAM/CRAM is preferable for region access, and VCF/BCF should retain sorted records and a valid header.

### Input sketch

**input**

```text
##fileformat=VCFv4.2
#CHROM	POS	ID	REF	ALT	QUAL	FILTER	INFO
chr1	10	.	A	G	50	PASS	.
```


## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `input` Input VCF/BCF | yes | empty | Select a complete real input file; do not submit the placeholder example path. VCF, bgzipped VCF, or BCF. |
| `threads` Threads | no | 0 | Keep `0` for automatic selection on large files; use `1` for small inputs or fixed sequential processing. 0 uses AUTO for large files; 1 disables parallel variant processing. |
| `output` Output TSV | no | empty | Leave blank to write inside the run directory; custom paths should stay in the workspace and use the correct extension. Leave blank to save in the run directory. |
| `overwrite` Overwrite output | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing an existing output. |

## Read the result

- Output formats: `TSV`
- The summary reports variants, samples, SNPs, indels, transitions, and transversions; check reference build, sample columns, and FILTER status.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.
- Thread count affects execution path and speed, not the scientific result. Investigate differences by comparing logs, inputs, and parameters.

## Minimal command example

```powershell
opentools run hts.vcf.stats --param input=C:\data\input.vcf
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- Confirm sorted order, indexes, reference build, and sequence dictionary; unsorted or mismatched inputs can fail or mislead.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [BAM Coverage](tool:hts.bam.coverage)
- [Filter VCF](tool:hts.vcf.filter)
- [Subset SAM/BAM/CRAM](tool:hts.bam.subset)

[Back to documentation home](opentools-doc:README.md)
