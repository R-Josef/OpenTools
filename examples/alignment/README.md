# Alignment and annotation import examples

These examples cover pure-Java conversion and import tasks:

```powershell
$jar = "..\..\opentools.jar"
$project = ".\alignment-project"

java -jar $jar --project $project run alignment.blast.xml-to-table `
  --param "input=.\blast.xml" --param "output=.\blast-table.tsv" --json

java -jar $jar --project $project run alignment.blast.reciprocal `
  --param "forward=.\forward.tsv" `
  --param "reverse=.\reverse.tsv" `
  --param "output=.\reciprocal.tsv" --json

java -jar $jar --project $project run annotation.pfam.import `
  --param "input=.\domains.domtbl" --param "output=.\pfam.tsv" --json

java -jar $jar --project $project run annotation.eggnog.import `
  --param "input=.\eggnog.tsv" --param "output=.\eggnog-normalized.tsv" --json

```

The included fixtures intentionally cover conversion and import tasks only.
`alignment.blast.local`, HMMER search, MUSCLE, trimAl, and IQ-TREE require
additional query, subject, HMM, or sequence files. See `docs/external-tools.md`
for discovery and external-program configuration.

BLAST databases are reused by subject content, makeblastdb version, program, and
database type. Set `refreshDatabase=true` to rebuild, `cacheEnabled=false` to
use only the task workspace, or `cacheDirectory` to choose an existing cache
location. The cache defaults to 10,240 MiB and 128 entries; entries above either
limit are removed from least recently used to newest, but entries leased by an
active BLAST process are kept until that process finishes.
