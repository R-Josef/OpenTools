# OpenTools plugins

## Plugins

Place each plugin JAR directly in this directory:

```text
plugins/
├── example-plugin.jar
└── data/
    └── example.plugin/
```

The plugin JAR must contain:

```text
META-INF/opentools-plugin.json
```

Example descriptor:

```json
{
  "id": "example.plugin",
  "name": "Example Plugin",
  "version": "1.0.0",
  "apiVersion": "1.0",
  "mainClass": "org.example.ExamplePlugin",
  "description": "Example OpenTools plugin",
  "author": "Example Author",
  "dependencies": ["required.plugin"],
  "requestedCapabilities": ["tool", "page", "data"]
}
```

The main class must implement `io.opentools.plugin.OpenToolsPlugin`. Public task
contracts use the `io.opentools.api.tool` package. The plugin context can
register headless tasks and Swing pages. Plugin tools become available to both
the Swing workbench and CLI.

Supported capabilities:

- `tool`: register headless `OpenTool` contributions.
- `page`: register Swing page factories.
- `data`: access `plugins/data/<plugin-id>/`.

Omitted capabilities default to all three. Unknown capabilities make the
plugin incompatible rather than silently granting access.

Plugin dependencies are other plugin IDs in the same directory. Dependencies
load first and their JARs are added to the dependent plugin's private class
path. Missing dependencies and dependency cycles are shown in diagnostics.

Enable or disable installed plugins:

```powershell
java -jar opentools.jar plugins inspect example.plugin
java -jar opentools.jar plugins enable example.plugin
java -jar opentools.jar plugins disable example.plugin
```

Plugin IDs are disabled by default. Installing a JAR does not execute its
code; enable it explicitly after reviewing its source, author, dependencies,
and requested capabilities.

The state is stored in `plugins/plugin-state.json`. Disabling a required
dependency prevents its dependants from loading.

The buildable reference plugin is `opentools-example-plugin`. Its JAR is copied
to `opentools-app/target/dist/examples/plugins/` by the full Maven build.

## Compile SDK

The general JAR distribution includes `plugin-sdk/` with:

```text
plugin-sdk/
  opentools-plugin-api-<version>.jar
```

Compile plugin code against this JAR with `--release 21`, but do not bundle it
into the plugin JAR. The host provides the same contracts at runtime. Maven
builds should declare `opentools-plugin-api` with `provided` scope as the
reference plugin does.

## Isolation and failure behavior

- Every plugin uses a separate child-first class loader.
- A failed plugin is reported in the Plugins page and does not stop the scan.
- Plugin tools and pages are removed when the plugin is disabled.
- Plugin data is stored under `plugins/data/<plugin-id>/`.
- New Java plugins run in the host JVM. Strong sandboxing is not provided. Only load
  plugins from sources you trust.
