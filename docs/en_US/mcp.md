# MCP integration

OpenTools exposes the same tool registry, validation, job store, and execution
service used by the GUI and CLI. MCP supports stdio and authenticated loopback
HTTP.

## stdio

```json
{
  "mcpServers": {
    "opentools": {
      "command": "OpenToolsCLI.exe",
      "args": [
        "--project",
        "C:\\work\\opentools-project",
        "mcp",
        "serve",
        "--transport",
        "stdio"
      ]
    }
  }
}
```

stdio stdout carries JSON-RPC only. Startup and diagnostics use stderr.

## Loopback HTTP

```powershell
OpenToolsCLI.exe --project C:\work\project mcp serve `
  --transport http --host 127.0.0.1 --port 0 --json
```

The startup envelope contains `data.endpoint` and `data.token`. Requests need:

```text
Authorization: Bearer <token>
Content-Type: application/json
```

The server binds only to loopback, checks Origin, limits request size, and uses a
bounded worker pool. The token is process-local. Do not put it in project files,
shared logs, or chat transcripts.

## Recommended call order

1. `opentools_search_tools`
2. `opentools_describe_tool`
3. `opentools_validate_inputs`
4. `opentools_submit_job`
5. `opentools_job_status`
6. `opentools_read_result`

A successful submission only means the job is queued. Wait for `SUCCEEDED`
before reading results.

For multi-step DAGs, use `opentools_run_workflow` with a workspace path to a
workflow JSON file. It returns per-step status, run IDs, artifacts, and metrics;
see [Workflows](workflow.md).

## Security boundaries

- Inputs, outputs, and directories are restricted to allowed workspaces.
- Automation rejects `overwrite=true`; use a new output path.
- HTTP has no TLS and is intended for same-machine clients.
- stdio access control belongs to the parent process.
