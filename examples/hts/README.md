# HTS examples

```powershell
$jar = "..\..\opentools.jar"
$project = ".\hts-project"

java -jar $jar --project $project run hts.sam-stats `
  --param "input=.\reads.sam" --param "output=.\sam-stats.tsv" --json

java -jar $jar --project $project run hts.bam.subset `
  --param "input=.\reads.sam" --param "regions=chr1:1-4" `
  --param "threads=0" --param "output=.\subset.bam" `
  --param "createIndex=true" --json

java -jar $jar --project $project run bed.coverage `
  --param "alignment=.\reads.sam" --param "regions=.\regions.bed" `
  --param "countOverlappingPairsOnce=false" `
  --param "threads=0" `
  --param "output=.\bed-coverage.tsv" --json

java -jar $jar --project $project run hts.vcf.stats `
  --param "input=.\variants.vcf" --param "threads=0" `
  --param "output=.\vcf-stats.tsv" --json

java -jar $jar --project $project run hts.vcf.filter `
  --param "input=.\variants.vcf" --param "minQual=20" `
  --param "maxMissing=0.5" --param "threads=0" `
  --param "indexType=AUTO" `
  --param "output=.\filtered.vcf" --json
```

SAM/BAM/CRAM and VCF/BCF access is provided by HTSJDK 6.0.0. When an indexed
BAM/CRAM is supplied, subset and coverage tasks query only the requested
intervals. Coverage keeps interval-boundary events instead of a per-base array.
Indexed subsets use independent readers per worker, deduplicate records across
merged regions, and merge coordinate-sorted worker output before creating the
BAI sidecar. The BAM and BAI are staged together and published as a pair; an
index-path conflict or failed sidecar move rolls back the primary output.
`threads=0` selects AUTO only for indexed inputs of at least 32 MiB,
`threads=1` is sequential, and `2-64` is an explicit upper bound.
Compressed VCF output gets a TBI by default; BCF output gets a CSI. Set
`indexType=NONE` for ordinary VCF or when an external indexing step is preferred.
VCF decisions can run in ordered record blocks, while the compressed
VCF/BCF writer and TBI/CSI index remain single-threaded. Dictionary order is
checked by that writer, so an unsorted or dictionary-mismatched output is never
published with an index.
