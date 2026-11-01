# Visualization examples

```powershell
$jar = "..\..\opentools.jar"
$project = ".\visualization-project"

java -jar $jar --project $project run plot.seqlogo --param "input=.\logo.tsv" --param "output=.\logo" --json
java -jar $jar --project $project run plot.seqlogo `
  --param "input=.\aligned-logo.fa" --param "inputMode=FASTA" `
  --param "pseudocount=0.5" --param "bothStrands=false" `
  --param "output=.\fasta-logo" --json
java -jar $jar --project $project run plot.gene-location --param "input=.\genes.gff3" --param "output=.\genes" --json
java -jar $jar --project $project run plot.domain --param "input=.\domains.tsv" --param "output=.\domains" --json
java -jar $jar --project $project run plot.motif --param "input=.\motifs.tsv" --param "output=.\motifs" --json
java -jar $jar --project $project run plot.circos --param "input=.\circos.tsv" --param "output=.\circos" --json
java -jar $jar --project $project run plot.efp --param "input=.\expression.tsv" --param "output=.\efp" --json
java -jar $jar --project $project run visual.venn --param "input=.\venn-sets.tsv" --param "output=.\venn" --json
java -jar $jar --project $project run plot.gene-location --param "input=.\gene-locations.gff3" --param "output=.\gene-locations" --json
java -jar $jar --project $project run plot.efp --param "input=.\efp-expression.tsv" --param "output=.\efp-diagnostic" --json
```

Every task emits a PlotModel plus configurable PNG/SVG/PDF artifacts.
`plot.seqlogo` accepts either the legacy position table or equal-length aligned
FASTA and exports the computed position matrix as `.processed.tsv`.
