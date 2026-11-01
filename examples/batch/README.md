# Batch execution example

Run from this directory:

```powershell
java -jar ..\..\opentools.jar `
  --project .\batch-project `
  batch run .\batch.json --json
```

The batch contains a FASTA statistics task and a GFF3 inspection task using the
included synthetic fixtures.
