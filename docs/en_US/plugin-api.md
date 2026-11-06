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

### Key construction

The localizer searches exact keys. It does not automatically prepend the plugin
ID. The standard tool UI constructs these keys from descriptor IDs:

| UI text | Key |
| --- | --- |
| Tool name | `tool.<Tool ID>.name` |
| Tool description | `tool.<Tool ID>.description` |
| Parameter title | `tool.<Tool ID>.parameter.<Parameter ID>.title` |
| Parameter description | `tool.<Tool ID>.parameter.<Parameter ID>.description` |
| Plugin Page title | `page.<Page ID>.title` |
| Tool category | Host-owned `category.<Category>` |
| Result table column | `result.<Tool ID>.column.<Normalized Column>` |
| Result table value | `result.<Tool ID>.value.<Normalized Value>` |

Plugin Pages and Interactive Tool Pages share the term "page" but not an
identity: a Plugin Page has a Page ID, while an Interactive Tool Page uses its
Tool ID and `tool.<Tool ID>.name`.

`ToolResult` supplies fallback text and message arguments. The host derives the
summary and detail keys from the current Tool ID. Keys passed to
`context.text(...)` and custom four-argument page titles are used exactly as
supplied.

Isolation comes from the plugin-owned localizer, not from adding a textual
prefix to every key. Identical keys in different plugins resolve against
different resource sets.

Result table display names normalize `%` to `" percent"`, lowercase the text,
replace runs of characters outside `a-z` and `0-9` with `_`, and trim
underscores. This affects display only, not stored result data.

Lookup order is:

1. the complete active code, such as `zh_CN`;
2. the language-only code, such as `zh`;
3. `en_US`;
4. the fallback passed by the caller.

Values use `java.text.MessageFormat` when arguments are supplied. The localizer
reads the active language on every lookup, so a language change affects plugin
metadata, page titles, tool catalogs, forms, documentation, interactive pages,
and result text without restarting the process.

`ToolDescriptor` and `ToolParameter` carry fallback display text, usually in
English. Those values remain the final fallback when a language file or key is absent.
The manifest `name` and `description` are fallbacks. When localization is
present, the plugin files always use the fixed keys `plugin.name` and
`plugin.description`:

```json
{
  "name": "Example Plugin",
  "description": "Example extension"
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

Current resource limits are:

| Item | Limit |
| --- | --- |
| Language files | 64 |
| One file | 256 KiB |
| Total resources | 1 MiB |
| Keys per file | 5,000 |
| Key length | 256 characters |
| Value length | 16,384 characters |

Language file names use `[A-Za-z]{2,8}(?:_[A-Za-z0-9]{2,8})?.properties`.
`en_US.properties` is the recommended fallback but is not required to load the
plugin.

## Optional tool Markdown

Plugin tool guides use a discoverable resource path rather than an API call:

```text
META-INF/opentools/docs/<language>/tool/<Tool ID>.md
```

The file name is the exact Tool ID plus `.md`. The host tries the full active
language, the language-only code, and finally `en_US`.

If a guide exists, OpenTools displays the custom Markdown, a horizontal
separator, and then the generated descriptor reference. Without a guide, only
the generated reference is shown. Missing guides do not fail plugin loading.

Tool links use `tool:<Tool ID>`. Links to the application documentation root use
`opentools-doc:<path>`. Plugin-relative images are not yet supported.

The complete resource discovery table is in the
[plugin development guide](plugin-development.md#resource-discovery-contract).

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
  <version>0.13.3-SNAPSHOT</version>
  <scope>provided</scope>
</dependency>
```

For a direct compiler invocation:

```powershell
javac --release 21 `
  -cp "plugin-sdk\opentools-plugin-api-0.13.3-SNAPSHOT.jar" `
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
