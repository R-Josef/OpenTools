# OpenTools MCP and CLI Reference

## Start MCP

Stdio transport:

```powershell
java -jar opentools.jar --project C:\work\project mcp serve --transport stdio
```

Authenticated loopback HTTP:

```powershell
java -jar opentools.jar --project C:\work\project mcp serve `
  --transport http --host 127.0.0.1 --port 0 --json
```

The HTTP response contains `endpoint` and a short-lived bearer `token` for the
current process. Send the token as `Authorization: Bearer <token>`. Do not write
the token into project files, logs, or shared messages.

## MCP Tools

- `opentools_search_tools`: find tasks by ID, name, category, format, or alias.
- `opentools_describe_tool`: return descriptor and parameter JSON Schema.
- `opentools_validate_inputs`: validate types, paths, and task-specific rules.
- `opentools_submit_job`: submit an asynchronous job.
- `opentools_job_status`: poll with optional `waitSeconds` (0-60).
- `opentools_cancel_job`: request cancellation.
- `opentools_retry_job`: retry from stored effective parameters.
- `opentools_read_result`: read the persisted result document.
- `opentools_list_jobs`: list recent project jobs.
- `opentools_run_batch`: validate or run a sequential batch of independent jobs.
- `opentools_run_workflow`: run a DAG from a workspace workflow JSON path; steps
  can consume `${step.output}`, `${step.output[N]}`, `${step.metric.KEY}`,
  and `${step.runId}` placeholders.
- `opentools_detect_format`: inspect a file in the allowed workspace.

Tool failures return MCP `isError: true` with structured
`code/message/suggestion/issues`.

## FASTA Example

```json
{
  "toolId": "fasta.stats",
  "parameters": {
    "input": "data/sample.fa"
  }
}
```

Relative paths resolve against the active project root.

## GFF Example

```json
{
  "toolId": "gxf.inspect",
  "parameters": {
    "input": "data/annotation.gff3"
  }
}
```

## Batch File

```json
{
  "continueOnError": false,
  "jobs": [
    {
      "toolId": "fasta.stats",
      "parameters": {"input": "data/sample.fa"},
      "dryRun": false
    },
    {
      "toolId": "gxf.inspect",
      "parameters": {"input": "data/annotation.gff3"},
      "dryRun": false
    }
  ]
}
```

Run it:

```powershell
java -jar opentools.jar --project C:\work\project batch run batch.json --json
```

Batch jobs run sequentially. `continueOnError=false` stops after the first
failure. Add `--dry-run` to validate without submitting.
