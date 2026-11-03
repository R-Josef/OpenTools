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

`--json` output is always UTF-8 and escapes non-ASCII characters as JSON
`\uXXXX`, so PowerShell `ConvertFrom-Json`, Python, and Node parse it correctly
even when the Windows console uses a legacy code page. Human-readable output
keeps the platform encoding. `run` waits for the job and returns a stable exit
code. To run in the background, submit from a persistent process such as the
GUI or the MCP server.

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

`jobs status` reports `progress` (0–1) and `progressMessage`. The concurrency
limit is configurable with `-Dopentools.jobs.maxConcurrent=N` (default: half the
CPU count, clamped to 1–4). Jobs left behind by an abnormal exit are marked
`JOB_INTERRUPTED` the next time the project opens.

`jobs cancel` also works across processes: it marks a queued or running job in
the shared project database, and the process that owns the job polls the request
within a few seconds and records `CANCELLED`.

## Batch execution

Batch files run independent jobs sequentially. Use a workflow when later steps
need an earlier step's artifact.

```powershell
OpenToolsCLI.exe --project C:\work\project batch run examples\batch\batch.json --json
```

## Workflows

Workflow files declare steps, dependencies, and `${step.output}` placeholders.
See [Workflows](workflow.md) for the format and placeholder rules.

```powershell
OpenToolsCLI.exe --project C:\work\project workflow run workflow.json --json
```

Directory watching and interval schedules are explicit commands:

```powershell
OpenToolsCLI.exe --project C:\work\project workflow watch C:\work\incoming --workflow workflow.json --pattern "*.fa"
OpenToolsCLI.exe --project C:\work\project workflow schedule --workflow workflow.json --every-seconds 3600
```

See [Workflows](workflow.md) for placeholders and opt-in behavior, and
[Errors and exit codes](errors-exit-codes.md) for automation behavior.
