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
  "version": "1.1.0",
  "apiVersion": "2.1",
  "mainClass": "org.example.ExamplePlugin",
  "description": "Example OpenTools plugin",
  "nameKey": "plugin.name",
  "descriptionKey": "plugin.description",
  "author": "Example Author",
  "dependencies": ["required.plugin"],
  "requestedCapabilities": ["tool", "page", "data"]
}
```

The main class must implement `io.opentools.plugin.OpenToolsPlugin`. Public tool
contracts use the `io.opentools.api.tool` package. The plugin context can
register headless tools, interactive tool pages, and standalone Swing pages.
Plugin tools become available to the Swing workbench, CLI, and MCP.

Plugin-owned language resources are packaged in the JAR and use UTF-8:

```text
META-INF/opentools/i18n/en_US.properties
META-INF/opentools/i18n/zh_CN.properties
```

The host binds one localizer per plugin and maps every registered Tool ID to
that owner. Plugins with identical keys remain isolated. Use
`PluginContext.text(...)` and `ToolUiContext.text(...)` for plugin text;
`ToolDescriptor` and `ToolParameter` fallback values remain the final fallback.
The host `lang/` files contain only application-wide UI and category keys.

Keys passed to `context.text(...)` are exact and are not automatically prefixed
with the plugin ID. Standard tool UI constructs `tool.<Tool ID>.name` and the
corresponding description and parameter keys. The three-argument `registerPage`
overload derives `page.<Page ID>.title`. See
`docs/<language>/plugin-development.md` for the complete ID, resource, result
table, and packaging contract.

Optional tool guides are also discovered from the plugin JAR:

```text
META-INF/opentools/docs/<language>/categories/<category>/<Tool ID>.md
```

No documentation registration call is required. If a guide is absent, the host
continues to show the generated descriptor reference.

Supported capabilities:

- `tool`: register `OpenTool` and `InteractiveOpenTool` contributions.
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

Plugin IDs are disabled by default. The release package ships a
`plugin-state.json` that enables the trusted default plugin. Installing a
third-party JAR does not execute its code until it is enabled.

The state is stored in `plugins/plugin-state.json`. Disabling a required
dependency prevents its dependants from loading.

The buildable reference plugin is `opentools-example-plugin`. Its JAR is copied
to the release `plugins/` directory and starts enabled by default.

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
- Tool ID to localizer bindings and language resource data are released when
  the plugin is disabled.
- Plugin data is stored under `plugins/data/<plugin-id>/`.
- Invalid, duplicate, or oversized language resources produce structured
  diagnostics and fall back without stopping the plugin host.
- New Java plugins run in the host JVM. Strong sandboxing is not provided. Only load
  plugins from sources you trust.
