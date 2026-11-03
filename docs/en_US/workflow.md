# Workflows

Run a dependency-ordered workflow from the CLI or MCP. Every step is a normal OpenTools task, so validation, workspace policy, jobs, logs, and result manifests still apply. Output artifacts of a successful step can be passed to later steps.

## Workflow file

```json
{
  "name": "fasta-summary-and-preview",
  "continueOnError": false,
  "steps": [
    {
      "id": "stats",
      "toolId": "fasta.stats",
      "parameters": {"input": "data/sample.fa"}
    },
    {
      "id": "preview",
      "toolId": "table.preview",
      "parameters": {"input": "${stats.output}"},
      "dependsOn": ["stats"]
    }
  ]
}
```

- `id` is required and unique within one workflow.
- `dependsOn` is optional; omitted or empty means the step can run immediately.
- `parameters` uses the same IDs and value types as `tools describe <tool-id>`.
- Relative paths resolve against the project root. The workspace policy still rejects paths outside the project or configured workspace roots.

## Placeholders

| Placeholder | Meaning |
| --- | --- |
| `${step.output}` | First artifact path produced by the step |
| `${step.output[0]}` | Artifact at a zero-based index |
| `${step.metric.KEY}` | Numeric or text value from the step result metrics |
| `${step.runId}` | Run identifier of the referenced step |

Placeholders are only available after the referenced step succeeds. A missing step, failed dependency, missing artifact, or missing metric stops that step with a structured error.

## Execution rules

- Steps run in topological order; cycles and unknown dependencies are rejected before any job starts.
- A workflow contains at most 100 steps, and each step has a one-hour automation timeout.
- With `continueOnError=false` (default), the first failure stops the workflow and remaining steps are marked `SKIPPED`.
- With `continueOnError=true`, independent steps continue; steps that depend on a failed step are skipped.
- Partial completion is reported per step with `status`, `runId`, `outputs`, `metrics`, and `errorCode`.

## CLI

```powershell
opentools --project C:\work\project workflow run C:\work\project\workflow.json --json
```

The JSON response includes per-step status and artifacts. A failed step exits with code 4; invalid workflow structure exits with code 2 or 4 depending on the error.

## MCP

Use the `opentools_run_workflow` tool with a workspace path:

```json
{"path": "workflow.json"}
```

The MCP tool returns the same per-step structure. Submit individual jobs when you need a custom client-side scheduler.

## GUI editor

Open **Workflows** in the main navigation to edit the same JSON format with a
live DAG preview:

- **New** loads a two-step template.
- **Validate** parses the JSON, checks duplicate IDs, missing dependencies, and
  cycles, then redraws the node graph.
- **Save** writes the workflow under `workflows/<name>.json` in the current
  project; saved files appear in the left list.
- **Run** executes steps sequentially with the same placeholders, failure
  policy, validation, and job records as the CLI/MCP runner.
- The run log and node colors show `SUCCEEDED`, `FAILED`, and `SKIPPED`.

The GUI runner is intentionally sequential; use the CLI/MCP workflow for
scripted or remote execution. The JSON remains the portable source of truth.

## Directory watch and schedules

Both features are **explicit opt-in CLI commands**; OpenTools never starts a
watcher or scheduler automatically.

### Watch a directory

```powershell
opentools --project C:\work\project workflow watch C:\work\incoming `
  --workflow workflows\import.json --pattern "*.fa" --max-runs 1
```

- The workflow can use `${watch.file}` and `${watch.name}` in parameter
  strings; the watcher substitutes the matched file before running.
- Matching uses a Java glob on the file name (default `*.fa`).
- A file must have a stable size for the debounce interval before it runs.
- `--max-runs 0` (default) watches until interrupted; `1` is useful for
  testing and one-shot drop folders.
- Watch and workflow paths must stay inside the allowed workspace.

### Run on an interval

```powershell
opentools --project C:\work\project workflow schedule `
  --workflow workflows\nightly.json --every-seconds 3600 --max-runs 1
```

- Runs immediately by default; use `--start-immediately=false` to wait one
  interval first.
- `--max-runs 0` runs until interrupted.
- There is no built-in cron parser; use the OS scheduler for calendar rules, or
  a short interval plus the workflow's own date logic.

### Operational notes

- A failed workflow returns a failed step summary; the watcher continues to the
  next file unless the process exits.
- The watcher handles create/modify events and ignores directories and
  non-matching names.
- These commands hold a project/runtime open for their lifetime; do not run two
  schedulers against the same output paths.

