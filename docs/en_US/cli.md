# CLI

Use `OpenToolsCLI.exe` from the portable package, or invoke the general JAR with
Java. The GUI entry is `OpenTools.exe`.

```powershell
OpenToolsCLI.exe tools list
OpenToolsCLI.exe tools describe fasta.stats --schema
OpenToolsCLI.exe doctor --json
OpenToolsCLI.exe run fasta.stats --param input=C:\data\genome.fa
```

## Projects and paths

```powershell
OpenToolsCLI.exe --project C:\work\project tools list
OpenToolsCLI.exe --app-home C:\work\opentools-app run fasta.stats `
  --param input=C:\data\genome.fa
```

Automation resolves relative paths against the project root. MCP and batch
operations can add explicit allowed workspaces.

## Tool parameters

Use `--param key=value`. Parameter IDs, types, defaults, and options are defined
by `tools describe <id> --schema`.

```powershell
OpenToolsCLI.exe run fasta.filter --param input=in.fa `
  --param minLength=100 --param output=out.fa.gz
```

## Jobs

```powershell
OpenToolsCLI.exe jobs list
OpenToolsCLI.exe jobs status <run-id>
OpenToolsCLI.exe jobs logs <run-id>
OpenToolsCLI.exe jobs cancel <run-id>
OpenToolsCLI.exe jobs retry <run-id>
```

## Batch execution

Batch files run jobs sequentially. They do not provide a DAG or automatically
connect one job's artifact to another job's input.

```powershell
OpenToolsCLI.exe --project C:\work\project batch run examples\batch\batch.json --json
```

See [Errors and exit codes](errors-exit-codes.md) for automation behavior.
