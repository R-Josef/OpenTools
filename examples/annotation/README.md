# Annotation and BED examples

These examples use a small synthetic GFF3 file and BED intervals.

```powershell
$jar = "..\..\opentools.jar"
$project = ".\annotation-project"

java -jar $jar --project $project run gxf.filter `
  --param "input=.\annotation.gff3" `
  --param "types=exon,mRNA" `
  --param "threads=0" `
  --param "output=.\features.gff3" --json

java -jar $jar --project $project run gxf.to-bed `
  --param "input=.\annotation.gff3" `
  --param "output=.\annotation.bed" --json

java -jar $jar --project $project run gxf.overlap `
  --param "input=.\annotation.gff3" `
  --param "sameTypeOnly=false" `
  --param "overlapMode=AUTO" `
  --param "maxActiveRecords=2000000" `
  --param "maxTempMegabytes=8192" `
  --param "output=.\overlaps.tsv" --json

java -jar $jar --project $project run gxf.representative `
  --param "input=.\annotation.gff3" `
  --param "scoreMode=LONGEST_TRANSCRIPT" `
  --param "output=.\representative.gff3" --json

java -jar $jar --project $project run bed.stats `
  --param "input=.\features.bed" `
  --param "threads=0" `
  --param "output=.\bed-stats.tsv" --json

java -jar $jar --project $project run bed.to-gff `
  --param "input=.\features.bed" `
  --param "output=.\from-bed.gff3" --json
```

GFF/GTF coordinates are 1-based inclusive. BED coordinates are 0-based
half-open. Conversion preserves a single-base feature as `start-1..end`.

`gxf.representative` now streams its first annotation pass and retains only
transcript metadata. `gxf.sequence.extract` uses a random-access FASTA index for
uniformly wrapped uncompressed genomes; gzip or non-standard wrapping falls back
to an in-memory sequence store.

DISK/AUTO overlap uses a temporary SQLite-sorted sweep. Sparse annotations keep
only currently overlapping records in memory. `maxActiveRecords` bounds a dense
active interval set, and `maxTempMegabytes` bounds the SQLite working file;
`SPILL_SPACE_LIMIT` and `ANNOTATION_ACTIVE_BOUND` fail without publishing a
partial report.
