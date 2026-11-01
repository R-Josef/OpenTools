# Table examples

```powershell
$jar = "..\..\opentools.jar"
$project = ".\table-project"

java -jar $jar --project $project run table.filter `
  --param "input=.\data.tsv" --param "column=group" --param "regex=^A$" `
  --param "numericColumn=value" --param "numericMin=15" `
  --param "threads=0" --param "output=.\filtered.tsv" --json

java -jar $jar --project $project run table.select-columns `
  --param "input=.\filtered.tsv" --param "columns=id,value" `
  --param "threads=0" --param "output=.\selected.tsv" --json

java -jar $jar --project $project run table.replace `
  --param "input=.\selected.tsv" --param "column=id" `
  --param "search=^" --param "replacement=gene_" `
  --param "threads=0" --param "output=.\replaced.tsv" --json

java -jar $jar --project $project run table.join `
  --param "left=.\data.tsv" --param "right=.\right.tsv" `
  --param "leftKey=id" --param "rightKey=id" --param "joinType=LEFT" `
  --param "output=.\joined.tsv" --json

java -jar $jar --project $project run table.pivot `
  --param "input=.\long.tsv" --param "rowColumn=gene" `
  --param "columnColumn=sample" --param "valueColumn=value" `
  --param "aggregation=MEAN" --param "output=.\pivot.tsv" --json

java -jar $jar --project $project run table.filter `
  --param "input=.\quoted.csv" --param "delimiter=AUTO" `
  --param "column=group" --param "regex=^A$" `
  --param "threads=0" --param "output=.\quoted-filtered.tsv" --json

java -jar $jar --project $project run table.transpose `
  --param "input=.\quoted-filtered.tsv" --param "transposeMode=DISK" `
  --param "maxTempMegabytes=8192" `
  --param "output=.\quoted-transposed.tsv" --json
```

`table.filter`, `table.select-columns`, and `table.replace` accept
`threads=0|1|2-64`. Input records are parsed in order, complete quoted records
are processed by bounded workers, and one writer emits results in original row
order. All three tasks keep `threads=0` sequential because parsing and the
ordered writer dominate in the measured workloads; explicit `2-64` remains
available for measured experiments. Use `threads=1` for strict sequential
processing.

Quoted delimiters and physical newlines are preserved as one logical field.
`table.join` accepts comma-separated compound keys on both sides and supports
INNER, LEFT, RIGHT, and FULL joins. Joins bound the right-side index with
`maxRightRows`; pivot has an explicit `maxCells` bound, transpose can spill to
disk with `transposeMode=AUTO|DISK`, `maxTempMegabytes` bounds the temporary
SQLite file, and
deduplication has `maxDistinctKeys`.
