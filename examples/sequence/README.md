# Sequence processing examples

These examples use synthetic FASTA and FASTQ records. Run the commands from this
directory in an extracted OpenTools distribution.

```powershell
$jar = "..\..\opentools.jar"
$project = ".\sequence-project"

java -jar $jar --project $project run fasta.filter `
  --param "input=.\sample.fa" `
  --param "threads=0" `
  --param "minLength=10" `
  --param "maxNPercent=10" `
  --param "output=.\filtered.fa" --json

java -jar $jar --project $project run fasta.transform `
  --param "input=.\nucleotides.fa" `
  --param "threads=0" `
  --param "direction=REVERSE_COMPLEMENT" `
  --param "sequenceType=NUCLEIC_ACID" `
  --param "output=.\reverse-complement.fa" --json

java -jar $jar --project $project run fasta.subsequence `
  --param "input=.\sample.fa" `
  --param "regionsFile=.\regions.tsv" `
  --param "output=.\subsequences.fa" --json

java -jar $jar --project $project run fasta.rename `
  --param "input=.\sample.fa" --param "mode=MAP" `
  --param "mapping=$(Get-Content .\rename-map.tsv -Raw)" `
  --param "output=.\renamed.fa" --json

java -jar $jar --project $project run fasta.convert `
  --param "input=.\sample.fa" --param "mode=FASTA_TO_TSV" `
  --param "output=.\sequences.tsv" --json

java -jar $jar --project $project run sequence.orf `
  --param "input=.\orf.fa" --param "minAminoAcids=2" `
  --param "threads=0" `
  --param "geneticCode=1" `
  --param "output=.\orfs.fa" --json

java -jar $jar --project $project run fastq.stats `
  --param "input=.\reads.fastq" --param "qualityEncoding=AUTO" `
  --param "threads=0" `
  --param "output=.\fastq-stats.tsv" --json

java -jar $jar --project $project run fastq.filter `
  --param "input=.\reads.fastq" --param "trimRight=1" `
  --param "threads=0" `
  --param "minLength=4" --param "minMeanQuality=20" `
  --param "qualityEncoding=PHRED_33" `
  --param "output=.\filtered.fastq" --json

java -jar $jar --project $project run fastq.stats `
  --param "input=.\phred64.fastq" --param "qualityEncoding=PHRED_64" `
  --param "output=.\phred64-stats.tsv" --json

java -jar $jar --project $project run fasta.convert `
  --param "input=.\multiline-sequences.tsv" --param "mode=TSV_TO_FASTA" `
  --param "output=.\multiline.fa" --json
```

Each run creates task reports under its run directory:

- `filter-report.tsv`: one row per input record with `KEPT` / `FILTERED` and reason.
- `transform-report.tsv`: inferred type, removed characters, and length changes.
- `subsequence-diagnostics.tsv`: missing IDs, duplicate source IDs, and out-of-range requests.
- `rename-report.tsv`: old/new IDs, duplicate adjustments, and unused mappings.
- `fastq-filter-report.tsv`: retained/filtered status and reason for each read.

`multiline-sequences.tsv` demonstrates RFC 4180-style quoted fields, including
embedded tabs and physical newlines. `phred64.fastq` contains Phred+64 qualities
and must be run with `qualityEncoding=PHRED_64` (or AUTO when the observed range
is unambiguous).

Coordinates in `regions.tsv` are 1-based and inclusive. Negative-strand output is
reverse-complemented after the forward-coordinate slice is taken. The file is
tab-separated and may be gzip-compressed. A single ID on a line extracts the
complete record.
