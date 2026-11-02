# Plugin API 2.1

OpenTools plugins compile against one self-contained JAR:

```text
plugin-sdk/opentools-plugin-api-<version>.jar
```

The JAR contains the plugin entry-point interfaces and the public execution
contracts used to build headless tools. It does not contain the OpenTools
runtime, SQLite layer, job service, plugin host, or desktop implementation.

Plugin extension types are under `io.opentools.plugin`. Public execution contracts
are under `io.opentools.api.tool`, for example `OpenTool`, `ToolDescriptor`,
`ToolRequest`, `ToolExecutionContext`, and `ToolResult`. Interactive tools add
`InteractiveOpenTool`, `ToolPanel`, and `ToolUiContext`. These packages are loaded from
the host at runtime and must not be duplicated inside a plugin.

## Extension contract

A plugin JAR contains its own classes and:

```text
META-INF/opentools-plugin.json
```

The descriptor names a class implementing `io.opentools.plugin.OpenToolsPlugin`.
During enablement the host creates that class and passes a `PluginContext` to
`onEnable`. The context can register:

- headless tools through `registerTool`;
- interactive tools by registering an `InteractiveOpenTool`;
- desktop pages through `registerPage`;
- private data and logs through the declared `data` capability.

## Plugin localization

Plugin-owned text stays inside the plugin JAR. Use UTF-8 properties files under
one fixed directory:

```text
META-INF/opentools/i18n/en_US.properties
META-INF/opentools/i18n/zh_CN.properties
```

The host binds one private localizer to each enabled plugin when the JAR loads.
Every registered Tool ID is mapped to the plugin that registered it; the host
does not infer plugin ownership from an ID prefix. Identical keys in different
plugins therefore remain isolated.

Resolve page and interactive-panel text through the host context:

```java
context.registerPage(
        "main",
        "Example Plugin",
        () -> page(context)
);
String title = context.text("page.main.title", "Example Plugin");
String label = toolUiContext.text("game.playerName", "Player");
String summary = context.text(
        "result.example.summary",
        "{0} rows completed",
        42
);
```

The three-argument overload derives `page.<id>.title`, so `"main"` above uses
`page.main.title`. Use the four-argument form when a page needs a custom key.

Lookup order is:

1. the complete active code, such as `zh_CN`;
2. the language-only code, such as `zh`;
3. `en_US`;
4. the fallback passed by the caller.

Values use `java.text.MessageFormat` when arguments are supplied. The localizer
reads the active language on every lookup, so a language change affects plugin
metadata, page titles, tool catalogs, forms, documentation, interactive pages,
and result text without restarting the process.

`ToolDescriptor` and `ToolParameter` continue to carry English display text.
Those values remain the final fallback when a language file or key is absent.
The descriptor may localize its own name and description with optional keys:

```json
{
  "name": "Example Plugin",
  "nameKey": "plugin.name",
  "description": "Example extension",
  "descriptionKey": "plugin.description"
}
```

Do not add plugin-private `tool.*`, `result.*`, or `page.*` keys to the host
`lang/` directory. The host-owned `category.*` keys, including
`category.plugin`, remain part of the application language files.

The loader validates UTF-8, duplicate keys, malformed properties, file count,
file and aggregate size, key count, and key/value length. Problems produce
structured plugin diagnostics, skip the affected resource, and keep startup
and unrelated plugins running. Resource lookup is limited to one level under
`META-INF/opentools/i18n/` in the owning plugin JAR and cannot address files
outside that JAR.

## Interactive tools

`OpenTool` remains a headless contract. A tool that needs a custom Swing page
implements `InteractiveOpenTool` and returns a fresh `ToolPanel` from
`createPanel(ToolUiContext)`.

The panel replaces the generated parameter form for that tool. The page may use
timers and keyboard input, but completed values must be submitted through
`ToolUiContext.submit(ToolRequest)`. This keeps validation, jobs, history,
results, CLI, MCP, and retry on the same execution contract.

`ToolParameter` values remain serializable data such as strings, numbers, files,
and enums. Never place a `JPanel` or another live Swing object in
`ToolDescriptor`, `ToolParameter`, or `ToolRequest`.

The host closes `ToolPanel` when selection changes, the project changes, or the
owning plugin unloads. Panels with timers must stop them in `close()`.

## Compile dependency

Maven plugin projects should use:

```xml
<dependency>
  <groupId>io.opentools</groupId>
  <artifactId>opentools-plugin-api</artifactId>
  <version>0.8.1-SNAPSHOT</version>
  <scope>provided</scope>
</dependency>
```

For a direct compiler invocation:

```powershell
javac --release 21 `
  -cp "plugin-sdk\opentools-plugin-api-0.8.1-SNAPSHOT.jar" `
  -d build\classes `
  src\main\java\example\ExamplePlugin.java
```

Do not package the API JAR inside the plugin. OpenTools already provides the
same API classes at runtime and shares them with plugin code through the host
class loader.

## Javadoc

The compile SDK contains both forms of API documentation:

```text
plugin-sdk/
  opentools-plugin-api-<version>.jar
  opentools-plugin-api-<version>-javadoc.jar
  javadoc/
    index.html
```

Open `javadoc/index.html` for offline browsing. IDE and Maven tooling can attach
or resolve the `-javadoc.jar` artifact.

## Runtime boundary

Plugins use child-first loading for their private classes and dependencies.
OpenTools shares the public API and execution contracts from the host loader so a
plugin and the host use identical class identities. The plugin JAR should
therefore contain only its own implementation and dependencies that are not
provided by the API.

Java plugins run inside the OpenTools JVM. Class-loader isolation is not a
security sandbox. Install and enable only trusted plugins after reviewing their
source, dependencies, requested capabilities, and licenses.

## Compatibility

Plugin API 2.1 is the current extension contract. The major version must
match the host. A plugin may request the current minor version or an older minor
version within the same major version. This release does not support legacy
Java panel plugins, `.plugin` script packages, or TBtools plugin binaries.

API 2.1 continues the Tool terminology introduced in API 2.0:

| API 1.0 | API 2.0 |
| --- | --- |
| `TaskContext` | `ToolExecutionContext` |
| `TaskResult` | `ToolResult` |
| `TaskExecutionService` | `ToolExecutionService` |
| `TaskAutomationService` | `ToolAutomationService` |

Plugins compiled against an older contract must be rebuilt against 2.1. There
are no deprecated localization aliases or dual host/plugin resource lookup
paths.
