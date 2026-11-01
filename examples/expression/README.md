# Expression and plot examples

```powershell
$jar = "..\..\opentools.jar"
$project = ".\expression-project"

java -jar $jar --project $project run expression.normalize `
  --param "input=.\counts.tsv" `
  --param "geneLengths=.\lengths.tsv" `
  --param "method=TPM" --param "output=.\tpm.tsv" --json

java -jar $jar --project $project run expression.correlation `
  --param "input=.\tpm.tsv" --param "method=PEARSON" `
  --param "threads=0" `
  --param "output=.\correlation.tsv" --json

java -jar $jar --project $project run expression.pca `
  --param "input=.\tpm.tsv" --param "groups=.\groups.tsv" `
  --param "components=2" --param "output=.\pca.tsv" --json

java -jar $jar --project $project run expression.differential-input `
  --param "input=.\counts.tsv" --param "groups=.\groups.tsv" `
  --param "output=.\differential-input.tsv" --json

java -jar $jar --project $project run plot.pca `
  --param "input=.\pca.tsv" --param "output=.\pca-plot" --json

java -jar $jar --project $project run plot.correlation `
  --param "input=.\correlation.tsv" --param "output=.\correlation-plot" --json

java -jar $jar --project $project run plot.volcano `
  --param "input=.\differential.tsv" --param "output=.\volcano" --json

java -jar $jar --project $project run plot.box `
  --param "input=.\tidy.tsv" --param "output=.\box" --json

java -jar $jar --project $project run plot.violin `
  --param "input=.\tidy.tsv" --param "output=.\violin" --json

java -jar $jar --project $project run plot.bar `
  --param "input=.\bars.tsv" --param "output=.\bar" --json
```

All plot tasks emit `plot.json`, SVG, PNG, and PDF by default. Use
`exportFormats=PNG`, `PNG_SVG`, or the default `PNG_SVG_PDF`.
