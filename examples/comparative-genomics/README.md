# Phylogeny and comparative genomics examples

```powershell
$jar = "..\..\opentools.jar"
$project = ".\comparative-project"

java -jar $jar --project $project run tree.newick.summary `
  --param "input=.\tree.nwk" --param "output=.\tree-summary.tsv" --json

java -jar $jar --project $project run tree.newick.subset `
  --param "input=.\tree.nwk" --param "taxa=speciesA`nspeciesC" `
  --param "output=.\subset.nwk" --json

java -jar $jar --project $project run tree.render `
  --param "input=.\tree.nwk" --param "output=.\tree" --json

java -jar $jar --project $project run mcscan.import `
  --param "input=.\collinearity.tsv" --param "output=.\collinearity-normalized.tsv" --json

java -jar $jar --project $project run plot.collinearity `
  --param "input=.\collinearity-normalized.tsv" --param "output=.\collinearity" --json

java -jar $jar --project $project run plot.dotplot `
  --param "input=.\points.tsv" --param "groupColumn=group" `
  --param "output=.\dotplot" --json

java -jar $jar --project $project run kaks.calculate `
  --param "input=.\pairs.fa" --param "output=.\kaks.tsv" --json
```

`alignment.iqtree`, `mcscan.run`, and their executables are discovered locally
and are not bundled.
