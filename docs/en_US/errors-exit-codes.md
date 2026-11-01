# Errors and exit codes

## CLI exit codes

| Exit code | Meaning | Typical case |
| --- | --- | --- |
| `0` | Success or queued | Task completed or `--no-wait` submitted |
| `2` | Input or parameter error | Missing file, unknown task, schema failure |
| `3` | External dependency error | Tool missing or cannot start |
| `4` | Execution failure | Data error, task exception, partial completion |
| `5` | Cancelled | Job or external process cancelled |
| `6` | Internal error | Unclassified host exception |

`--json` emits one envelope on stdout:

```json
{
  "ok": false,
  "apiVersion": "1.0",
  "data": null,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Input FASTA is required.",
    "suggestion": "Choose an existing file.",
    "issues": [],
    "details": {}
  }
}
```

## Common error codes

| Code | User action |
| --- | --- |
| `INVALID_ARGUMENTS` | Check command arguments |
| `VALIDATION_FAILED` | Fix fields identified by `issues[].field` |
| `TOOL_NOT_FOUND` | Run `tools list` |
| `JOB_NOT_FOUND` | Get a valid run ID from `jobs list` |
| `EXTERNAL_TOOL_MISSING` | Use the portable package or configure a path |
| `EXTERNAL_PROCESS_START_FAILED` | Check executable, permissions, and architecture |
| `PATH_OUTSIDE_WORKSPACE` | Use a project path or add an allowed workspace |
| `OVERWRITE_NOT_ALLOWED` | Use a new output path |
| `RESULT_NOT_AVAILABLE` | Wait for a terminal job state |
| `DELIMITED_TABLE_MALFORMED` | Check quotes, escapes, and multiline fields |
| `TABLE_MEMORY_BOUND` | Reduce input or increase the relevant `max*` limit |
| `ANNOTATION_MEMORY_BOUND` | Partition annotation input |
| `ANNOTATION_ACTIVE_BOUND` | Adjust `maxActiveRecords` or partition intervals |
| `SPILL_SPACE_LIMIT` | Increase temporary-space limits or split input |
| `SPILL_DISK_FULL` | Free temporary disk space |
| `FASTQ_QUALITY_INVALID` | Check quality characters and encoding |
| `GENETIC_CODE_UNSUPPORTED` | Use a code declared in the schema |
| `VCF_INDEX_DICTIONARY_REQUIRED` | Add a `##contig` sequence dictionary |
| `VCF_NOT_SORTED` | Sort records in dictionary order |
| `CANCELLED` | Resubmit if needed |
| `INTERNAL_ERROR` | Preserve `run.log` and diagnostics |

## MCP errors

- Invalid or missing parameters: JSON-RPC `-32602`
- Unknown method: `-32601`
- Invalid JSON: `-32700`
- Task failure: `tools/call` returns `isError=true` with structured details

Start with the message and suggestion, then check the field-specific issue,
Environment Check, `manifest.json`, and `run.log`.
