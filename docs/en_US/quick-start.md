# Quick start

## 1. Choose how to run OpenTools

Windows users should extract the portable package and start `OpenTools.exe`.
Use `OpenToolsCLI.exe` for terminal, batch, or MCP workflows. Developers with
Java 21 can run the general JAR.

## 2. Create or open a project

The first launch opens the default local project or lets you create another one.
A project contains:

- `project.json` for project metadata;
- `.opentools/project.db` for job history;
- `data/` for user data;
- `runs/` for parameters, manifests, logs, and outputs;
- `results/` for reusable outputs.

Wait for queued and running jobs to finish before switching projects.

## 3. Find a task

Open Tasks from the main navigation. The left side groups tasks by category and
supports global search. Selecting a task shows required, optional, and advanced
parameters on the right.

## 4. Validate and run

1. Choose the input file.
2. Confirm the detected format.
3. Set task parameters. A blank output usually means the run directory.
4. Select **Validate** and resolve errors or warnings.
5. Select **Run** and monitor the job in Jobs or Results.

Existing outputs are not overwritten by default.

## 5. Inspect provenance and results

Every run writes `manifest.json` with input hashes, normalized parameters, tool
versions, Java and OS information, and output metadata. Open `run.log` when the
structured diagnosis is not sufficient.

## 6. Open Docs

Docs follows the active interface language. English reads from `docs/en_US`;
Simplified Chinese reads from `docs/zh_CN`. Changing the language in Settings
reloads the application UI immediately without restarting the process.
