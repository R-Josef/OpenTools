---
name: opentools
description: Run local OpenTools bioinformatics tasks through the CLI or MCP, including validation, job monitoring, result retrieval, batch runs, plots, and plugin-provided tools. Use when a request involves OpenTools sequence, annotation, table, or visualization automation.
---

# OpenTools

Use the existing OpenTools task contracts instead of reimplementing bioinformatics
logic. Treat JSON output as the automation API.

## Workflow

1. Discover the task with `opentools_search_tools` or
   `java -jar <opentools.jar> tools list --json`.
2. Inspect its Draft 2020-12 schema with `opentools_describe_tool` or
   `tools describe <tool-id> --schema`.
3. Validate parameters before submission.
4. Submit asynchronously, poll or wait for a terminal state, then read
   `result.json`.
5. Report the run ID, status, artifacts, and actionable error fields.

Use MCP when it is connected. Otherwise use the CLI. Both call the same
`ToolExecutionService` and `ToolRegistry`.

## Required Boundaries

- Keep input and output paths inside the active project or an explicitly
  configured workspace root.
- Never set `overwrite=true` in automation. Choose a new output path instead.
- Do not upload data or use remote services unless the user explicitly asks.
- Do not claim success from submission alone. Poll `job_status` and confirm
  `SUCCEEDED` before reading completed results.
- Preserve the returned run ID and artifact paths for provenance.

## CLI Contract

Every `--json` response uses this envelope:

```json
{
  "ok": true,
  "apiVersion": "2.0",
  "data": {},
  "error": null
}
```

Stable exit codes:

- `0`: command/job succeeded or was successfully queued.
- `2`: invalid input, usage, or unknown task.
- `3`: missing external dependency.
- `4`: tool execution failed or only partially completed.
- `5`: cancelled.
- `6`: internal error.

For detailed MCP tools, batch format, and command examples, read
[references/mcp-tools.md](references/mcp-tools.md).
