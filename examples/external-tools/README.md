# External tool examples

These examples require separately installed external programs. OpenTools first
uses packaged `tools/`, then explicit task paths, `OPENTOOLS_*` variables, the
inherited `PATH`.

## Protein workflow

```powershell
$jar = "..\..\opentools.jar"
$project = ".\external-tools-project"

java -jar $jar --project $project run alignment.muscle `
  --param "input=.\protein-unaligned.fa" `
  --param "output=.\aligned.fa" --json

java -jar $jar --project $project run alignment.trim `
  --param "input=.\aligned.fa" `
  --param "output=.\trimmed.fa" --json

java -jar $jar --project $project run alignment.iqtree `
  --param "input=.\trimmed.fa" --param "model=LG" `
  --param "output=.\tree.treefile" --json
```

## HMMER

Build and press an HMM before HMMER search:

```powershell
hmmbuild model.hmm aligned.fa
hmmpress -f model.hmm
```

Pass `hmmerBin` to hmmscan or hmmsearch when the executable directory is not on
`PATH`.

## MCScanX

`annotation.gff3` and `hits.blast` are minimal fixtures for `mcscan.input`. The
generated `.gff` uses the standard MCScanX format:

```text
sequence_id	gene_id	start	end
```

These fixtures do not replace a full upstream MCScanX validation.
