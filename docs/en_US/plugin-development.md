# Plugin developer guide

OpenTools plugins use Plugin API 2.1 and register tools or pages through an
isolated class loader. A plugin JAR needs
`META-INF/opentools-plugin.json`.

## Minimal descriptor

```json
{
  "id": "example.plugin",
  "name": "Example Plugin",
  "version": "1.0.0",
  "apiVersion": "2.1",
  "mainClass": "com.example.ExamplePlugin",
  "description": "Example extension",
  "nameKey": "plugin.name",
  "descriptionKey": "plugin.description",
  "dependencies": [],
  "requestedCapabilities": ["tool", "page", "data"]
}
```

Declare only the `tool`, `page`, and `data` capabilities the plugin uses.

## Bundled localization

Keep plugin language resources in the plugin JAR:

```text
META-INF/opentools/i18n/en_US.properties
META-INF/opentools/i18n/zh_CN.properties
```

Files use UTF-8. `en_US` is the plugin fallback, and missing keys fall back to
the English text in `ToolDescriptor` or `ToolParameter`. The host binds one
localizer per plugin at load time and maps each Tool ID to that localizer.

Use `PluginContext.text(...)` while registering pages and
`ToolUiContext.text(...)` inside interactive panels. Resource values support
`MessageFormat` arguments. Do not read plugin files directly from the host
`lang/` directory or manually search a resource path.

The example plugin provides:

```ini
plugin.name=Example Plugin
plugin.description=Default OpenTools plugin
page.main.title=Example Plugin

tool.plugin.example.coin-runner.name=Coin Runner
tool.plugin.example.coin-runner.param.playerName.title=Player name
result.plugin.example.coin-runner.summary=Coin Run completed: {0}, {1} m, {2} points
```

Register the page with both its resource key and English fallback:

```java
context.registerPage(
        "main",
        "page.main.title",
        "Example Plugin",
        () -> page(context)
);
```

Malformed properties, duplicate keys, or resources over the documented limits
appear as structured plugin diagnostics. The affected resource is skipped
while the plugin and host continue to load.

## Build and enable

```powershell
mvn -pl opentools-example-plugin -am package
```

Put the JAR in the release `plugins/` directory. Third-party plugins are
disabled unless enabled explicitly. Release packages seed the trusted default
plugin through `plugins/plugin-state.json`.

```powershell
OpenToolsCLI.exe plugins inspect example.plugin
OpenToolsCLI.exe plugins enable example.plugin
OpenToolsCLI.exe plugins list
OpenToolsCLI.exe plugins disable example.plugin
```

## Lifecycle

The host validates descriptors and API versions, resolves dependencies, checks
capabilities, loads private dependencies, and rolls back after enable failures.
Plugins must use public APIs and their private data directory.

## Compiler SDK

The general JAR distribution includes `plugin-sdk/opentools-plugin-api-<version>.jar`.
Compile with Java 21 and this JAR on the compiler class path, but do not bundle
it into the plugin. The host provides the same contracts at runtime.

Maven plugins should depend on `opentools-plugin-api` with `provided` scope.
The API JAR contains both extension interfaces and the public execution contracts
needed to implement a plugin tool.

See [Plugin API 2.1](plugin-api.md) for the complete compile, runtime, Javadoc,
and compatibility boundary.

## Security boundary

Java plugins run in the host JVM. Class-loader isolation is not a security
sandbox. Enable only trusted code and review its source, dependencies,
capabilities, and license before use.

## Release checklist

- valid API and dependency declarations;
- minimal capabilities;
- traversal-safe archives and no unpacking bomb;
- complete tool schemas;
- correct interactive panel lifecycle and standard request submission;
- UTF-8 plugin-localized descriptors, pages, tools, parameters, and results;
- language fallback, key isolation, diagnostics, and disable cleanup;
- shared GUI, CLI, MCP contracts;
- disable and rollback coverage;
- complete license and third-party notices.
