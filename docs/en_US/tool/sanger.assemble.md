# Sanger Assembly

`sanger.assemble` · Sequence · v1.2.0

## What this tool does

Assemble AB1, FASTQ, or FASTA Sanger reads by overlap, optionally anchor them to a reference, and report consensus provenance.

It performs one explicit, reproducible analysis step. The run record preserves normalized parameters, input hashes, and logs; always review the source data, settings, and biological interpretation before publication.

## When to use it

- Inspect, convert, or clean FASTA/FASTQ data before assembly, annotation, alignment, or downstream analysis.
- Use it when one step needs preserved parameters, logs, and outputs for repeatability or troubleshooting.

## Prepare the input

- Declared formats: `AB1`, `FASTQ`, `FASTA`
- Required parameters: `reads`
- Check that identifiers are unambiguous and FASTA/FASTQ records are complete; gzip files must preserve the original record structure.

## Recommended workflow

1. Confirm that file formats, identifiers or column names, and coordinate conventions agree.
2. Validate with defaults or the recommendations below to catch paths, headers, and type errors first.
3. After submission, inspect the summary, result table, and artifacts instead of relying only on the success status.
4. Keep the original inputs, parameters, run log, and output hashes when the result must be reproducible.

## How to choose parameters

| Parameter | Required | Default | Guidance |
| --- | --- | --- | --- |
| `reads` Read files | yes | empty | Select multiple read files. AB1/FASTQ/FASTA are supported; one file may contain multiple reads. |
| `reference` Reference FASTA | no | empty | Select a complete real input file; do not submit the placeholder example path. Optional reference sequence used to anchor reads and fill uncovered positions. |
| `minOverlap` Minimum overlap | no | 30 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum overlap length for joining reads. |
| `minIdentity` Minimum overlap identity | no | 0.85 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum fraction of matching bases in an overlap. |
| `trimQuality` End-trim quality | no | 20 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Mean Phred quality required in the end-trimming window. |
| `trimWindow` End-trim window | no | 8 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Sliding window used to trim low-quality read ends. |
| `minReadLength` Minimum read length | no | 50 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Reads shorter than this after trimming are rejected. |
| `trimAdapters` Trim primers/vectors | no | true | Start with the default and change it only after understanding the explanation. Trim common sequencing primers and optional custom vector sequences from read ends. |
| `adapterFile` Primer/vector FASTA | no | empty | Select a complete real input file; do not submit the placeholder example path. Optional FASTA of primer or vector sequences to trim from either read end. |
| `minAdapterMatch` Minimum primer match | no | 12 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum matching bases required for primer/vector trimming. |
| `trimVectorEnds` Trim vector ends with reference | no | true | Start with the default and change it only after understanding the explanation. Use the reference alignment to remove unaligned contig ends and write insert.fa. |
| `minVectorOverhang` Minimum aligned span | no | 20 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum aligned span retained in the vector-trimmed insert. |
| `consensusThreshold` Consensus threshold | no | 0.6 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum quality-weighted base fraction for a confident call. |
| `heterozygousThreshold` Heterozygous threshold | no | 0.25 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum second-base fraction for an IUPAC heterozygous call. |
| `minHeterozygousCoverage` Heterozygous coverage | no | 4 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum coverage before a heterozygous call is considered. |
| `minIndelSupport` Minimum indel support | no | 1 | Start from the default; document why a threshold changes and relate it to input scale or the statistical goal. Minimum reads supporting an insertion or deletion in the expanded consensus. |
| `outputDirectory` Output directory | no | empty | Choose a writable directory and check disk space and naming conflicts before batch output. Leave blank to save in the run directory. |
| `overwrite` Overwrite outputs | no | false | Keep `false` unless the target is known to be safe to replace. Allow replacing existing output files. |

## Read the result

- Output formats: `FASTA`, `TSV`, `BED`
- Review assembly-quality.tsv and warnings.tsv first, then trimming.tsv and the read/overlap table; assembly.fa is the full de novo contig and insert.fa uses the reference alignment to remove unaligned vector ends; corrected.fa uses reference coordinates, consensus_with_indels.fa expands supported insertions/deletions into the final sequence; indels.tsv and variants.tsv record indels and heterozygous sites, and provenance.bed marks READ_CONSENSUS, HETEROZYGOUS, DELETION, LOW_CONFIDENCE, and REFERENCE_FILL. Never treat reference-filled positions as sequencing evidence.
- The result panel is the quick-review surface; complete data, manifests, and logs live in the run directory.

## Minimal command example

```powershell
opentools run sanger.assemble --param reads=C:\data\reads-1.dat,C:\data\reads-2.dat
```

Replace placeholder paths with real workspace files. Keep parameter IDs unchanged and use `true` or `false` for booleans.

## Checks and failure boundaries

- FASTA/FASTQ records, IDs, and base or quality characters must be complete; damaged or truncated records fail the job.
- Existing outputs are protected by default; only consider `overwrite` after explicitly choosing the target path.

## Related next steps

- [Extract FASTA by ID](tool:fasta.extract)
- [FASTA Statistics](tool:fasta.stats)
- [Predict ORFs](tool:sequence.orf)

[Back to documentation home](opentools-doc:README.md)
