# Enrichment examples

```powershell
$jar = "..\..\opentools.jar"
$project = ".\enrichment-project"

java -jar $jar --project $project run go.enrich `
  --param "annotations=.\go-annotations.tsv" `
  --param "genes=.\target-genes.txt" `
  --param "background=.\background-genes.txt" `
  --param "output=.\go-enrichment.tsv" --json

java -jar $jar --project $project run go.level `
  --param "hierarchy=.\go-hierarchy.tsv" `
  --param "output=.\go-levels.tsv" --json

java -jar $jar --project $project run plot.enrichment-bar `
  --param "input=.\go-enrichment.tsv" `
  --param "output=.\enrichment-bar" --json

java -jar $jar --project $project run plot.enrichment-bubble `
  --param "input=.\go-enrichment.tsv" `
  --param "output=.\enrichment-bubble" --json
```

`kegg.enrich` uses the same two-column gene/pathway annotation and gene-list
contract as `go.enrich`. It never downloads annotations or calls online services.
