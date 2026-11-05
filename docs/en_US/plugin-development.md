# Plugin developer guide

This guide starts from the plugin JAR that OpenTools actually loads. Use Maven
for the build, but the deliverable is still one regular JAR placed in
`plugins/`.

## What goes into the JAR

Every plugin JAR must contain:

```text
META-INF/opentools-plugin.json
your/plugin/classes/...
META-INF/opentools/i18n/en_US.properties   # recommended
META-INF/opentools/i18n/zh_CN.properties   # optional
```

The manifest describes the plugin. A page-only plugin can start with:

```json
{
  "id": "my.plugin",
  "name": "My Plugin",
  "version": "1.0.0",
  "apiVersion": "2.1",
  "mainClass": "com.example.plugin.MyPlugin",
  "description": "My OpenTools extension",
  "dependencies": [],
  "requestedCapabilities": ["page"]
}
```

### Manifest fields

| Field | Required | Meaning |
| --- | --- | --- |
| `id` | yes | Plugin ID. Use only `A-Z`, `a-z`, `0-9`, `.`, `_`, and `-`. |
| `name` | yes | Fallback display name. |
| `version` | yes | Plugin version, independent of the OpenTools version. |
| `apiVersion` | yes | Plugin API used by the plugin. Current value: `2.1`. |
| `mainClass` | yes | Class implementing `io.opentools.plugin.OpenToolsPlugin`. |
| `description` | no | Fallback description. |
| `author` | no | Author or maintainer text. |
| `dependencies` | no | Other OpenTools plugin IDs, not Maven coordinates. |
| `requestedCapabilities` | no | Any of `tool`, `page`, and `data`. Omitted means all three. |

Plugin display text always uses the fixed keys `plugin.name` and
`plugin.description`. Manifest `name` and `description` remain the fallbacks.

## Smallest working plugin

```java
package com.example.plugin;

import io.opentools.plugin.OpenToolsPlugin;
import io.opentools.plugin.PluginContext;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class MyPlugin implements OpenToolsPlugin {
    @Override
    public void onEnable(PluginContext context) {
        context.registerPage(
                "main",
                "My Plugin",
                () -> mainPage(context)
        );
    }

    private static JPanel mainPage(PluginContext context) {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        panel.add(new JLabel(context.text(
                "page.main.heading",
                "My Plugin"
        )));
        return panel;
    }
}
```

The three page arguments are:

- `"main"`: a page ID, unique inside this plugin;
- `"My Plugin"`: the English fallback title;
- `() -> mainPage(context)`: a factory called when the user opens the page.

The page title key is derived automatically:

```text
main -> page.main.title
settings -> page.settings.title
```

Use the four-argument `registerPage` method only when you need a custom key.
Tools are registered separately with `context.registerTool(new MyTool())`.
See [Plugin API 2.1](plugin-api.md) for the tool contracts.

## ID rules

IDs are part of the runtime and resource-key contract. Choose them before
releasing a plugin and keep them stable.

| ID | Scope | Used for |
| --- | --- | --- |
| Plugin ID | Unique across installed plugins | Manifest, data directory, plugin dependencies |
| Tool ID | Unique across the host tool registry | Tool registration and standard tool text keys |
| Parameter ID | Unique inside one tool | Request map and parameter text keys |
| Page ID | Unique inside one plugin | Plugin page registration and default page title key |

Important details:

- A Tool ID is not automatically prefixed with the plugin ID.
- A Tool ID is also not automatically prefixed with `plugin.`.
- Two plugins cannot register the same Tool ID because the host tool registry is global.
- The host qualifies a page ID internally as `<plugin-id>.<page-id>`, but this
  qualified value is not automatically used as the page title resource key.
- Parameter IDs become JSON field names. Do not translate or rename them for
  localization.

## Resource discovery contract

This table is the complete naming contract. It intentionally uses placeholders
instead of example IDs.

| Purpose | Contract path or key | Found in |
| --- | --- | --- |
| Plugin manifest | `META-INF/opentools-plugin.json` | Plugin JAR |
| Localization file | `META-INF/opentools/i18n/<Language>.properties` | Plugin JAR |
| Tool Guide | `META-INF/opentools/docs/<Language>/tool/<Tool ID>.md` | Plugin JAR |
| Plugin name | `plugin.name` | Localization file |
| Plugin description | `plugin.description` | Localization file |
| Plugin Page title | `page.<Page ID>.title` | Localization file |
| Tool name | `tool.<Tool ID>.name` | Localization file |
| Tool description | `tool.<Tool ID>.description` | Localization file |
| Tool parameter title | `tool.<Tool ID>.parameter.<Parameter ID>.title` | Localization file |
| Tool parameter description | `tool.<Tool ID>.parameter.<Parameter ID>.description` | Localization file |
| Result summary | `result.<Tool ID>.summary` | Localization file |
| Result detail | `result.<Tool ID>.detail` | Localization file |
| Result table column | `result.<Tool ID>.column.<Normalized Column>` | Localization file |
| Result table value | `result.<Tool ID>.value.<Normalized Value>` | Localization file |
| Tool category | `category.<Category>` | Host language file |

Files use `/` because they are resource paths. Localization keys use `.` because
they are flat key paths. IDs remain opaque even when an ID itself contains dots.

## Localization

Plugin localization is a convention-based resource lookup. Put UTF-8
`.properties` files in exactly this directory inside the plugin JAR:

```text
META-INF/opentools/i18n/en_US.properties
META-INF/opentools/i18n/zh_CN.properties
```

The host loads these files when the plugin is enabled. The plugin author does
not need to create a `ResourceBundle` or upload keys to a registry.

### Keys are not automatically rewritten

This is important: **OpenTools does not automatically prepend
`tool.plugin.<plugin-id>` to arbitrary keys.**

For example:

```java
context.text("game.start", "Start")
```

looks for the literal key:

```properties
game.start=启动
```

It does not search for
`tool.plugin.my.plugin.game.start`.

Isolation comes from the localizer, not string rewriting. Each plugin can use
the same key such as `game.start` because its localizer can only see resources
from its own JAR.

### Key construction at a glance

| Text shown by the host | Resource key | Who constructs it |
| --- | --- | --- |
| Tool name | `tool.<Tool ID>.name` | Host |
| Tool description | `tool.<Tool ID>.description` | Host |
| Parameter title | `tool.<Tool ID>.parameter.<Parameter ID>.title` | Host |
| Parameter description | `tool.<Tool ID>.parameter.<Parameter ID>.description` | Host |
| Plugin Page title | `page.<Page ID>.title` | Host |
| Four-argument page title | Key passed by the plugin | Plugin |
| Tool category | `category.<lowercase category>` | Host language files |
| Tool search terms | `tool.<Tool ID>.search` | Host language files |
| Result table column | `result.<Tool ID>.column.<normalized column>` | Host |
| Result table value | `result.<Tool ID>.value.<normalized value>` | Host |
| Result summary/detail | `result.<Tool ID>.summary` / `detail` | Host |
| Custom panel text | Key passed to `context.text(...)` | Plugin |

`page.*` belongs only to a Plugin Page opened from the Plugins view by "Open
plugin page". An
Interactive Tool Page has no separate Page ID: its identity is the Tool ID and
its title uses `tool.<Tool ID>.name`.

Category localization is application-owned. A plugin tool normally uses the
descriptor category `Plugin`, which maps to the host key `category.plugin`.
Putting `category.plugin` in a plugin JAR does not localize the category shown
by the host.

`tool.<Tool ID>.search` is an optional comma-separated keyword list. The
in-app search reads the localized name, description, category, parameter text,
formats, stable ID, and this value. Descriptor aliases are not used by the UI,
so a localized result is never matched through a hidden English-only alias.

For result tables, the host normalizes the displayed column or cell text:

```text
lowercase
% becomes " percent"
every run of non a-z/0-9 characters becomes "_"
leading and trailing "_" are removed
```

For example:

```text
"Distance (m)" -> distance_m
"SECOND_CLICK" -> second_click
```

This changes display text only. Stored result JSON and exported data remain
unchanged.

### Recommended key conventions

Use complete, readable keys. IDs should identify the data or operation without
repeating the top-level resource type:

```text
ToolDescriptor.id = greeting
Parameter ID = recipient

tool.greeting.name
tool.greeting.description
tool.greeting.parameter.recipient.title
tool.greeting.parameter.recipient.description
result.greeting.summary
result.greeting.detail
```

Do not write `greeting.tool`; the leading `tool.` already identifies the
resource type. Do not use `name`, `title`, or `description` as a parameter ID;
those values describe UI fields, not the parameter's data.

The default example plugin uses the Tool ID `example.coin-runner`, so its
complete keys begin with `tool.example.coin-runner`. The `example` portion is
part of that Tool ID, not text automatically added by the host.

Page titles use:

```properties
page.main.title=My Plugin
```

Manifest metadata uses:

```properties
plugin.name=My Plugin
plugin.description=My OpenTools extension
```

You may use other keys for your own Swing components. Call
`PluginContext.text(...)` while creating a page or
`ToolUiContext.text(...)` inside an interactive tool:

```java
String start = context.text("game.start", "Start");
String message = context.text(
        "result.greeting.summary",
        "Processed {0} rows",
        42
);
```

Resource values use `java.text.MessageFormat` when arguments are supplied.
Lookup order is:

```text
active full language -> active language -> en_US -> caller fallback
```

Without language files, the plugin still loads and displays the fallback text.
Hard-coded plugin text also does not fail; it simply does not translate.

### Values that are not automatically localized

The standard text resolver does not translate functional descriptor data:

- `ToolParameter.options`, because these values are submitted and validated;
- `ToolParameter.defaultValue`, when it has execution semantics;
- `ToolDescriptor.aliases`, input formats, and output formats;
- result rows stored in JSON or exported files.

Keep these values stable and language-neutral. For example, use
`SECOND_CLICK` as an execution value and localize only its displayed table text
with the normalized result value key.

Do not put plugin-private `tool.*`, `result.*`, or `page.*` keys into the host
`lang/` directory. The host category `category.plugin` is application-owned.

Malformed UTF-8, duplicate keys, broken Properties, and oversized resources
produce plugin diagnostics. The affected file is skipped and the plugin falls
back instead of stopping the whole application.

### Resource file limits and validation

| Rule | Limit or format |
| --- | --- |
| Encoding | Strict UTF-8 |
| Location | Directly under `META-INF/opentools/i18n/` |
| Language file name | `[A-Za-z]{2,8}(?:_[A-Za-z0-9]{2,8})?.properties` |
| Language files per plugin | At most 64 |
| One file | At most 256 KiB |
| All language files | At most 1 MiB |
| Keys in one file | At most 5,000 |
| One key | At most 256 characters |
| One value | At most 16,384 characters |

`en_US.properties` is the recommended plugin fallback file, but it is not
required to load the plugin. Duplicate keys and malformed Properties cause the
affected language file to be skipped. Nested language directories are ignored
with a diagnostic.

When arguments are supplied, message patterns use `MessageFormat`. Resource
loading does not validate every message pattern, so an invalid pattern can
throw when that text is rendered.

## Optional tool Markdown

Plugins can provide richer task documentation without a registration API. The
host discovers Markdown by convention while loading the plugin JAR:

```text
META-INF/opentools/docs/<language>/tool/<Tool ID>.md
```

For example:

```text
Tool ID: example.coin-runner

META-INF/opentools/docs/en_US/tool/example.coin-runner.md
META-INF/opentools/docs/zh_CN/tool/example.coin-runner.md
```

The file name is the exact Tool ID plus `.md`. Tool IDs supported by this file
lookup use `A-Z`, `a-z`, `0-9`, `.`, `_`, and `-`.

Language fallback is:

```text
full active language -> language-only code -> en_US
```

When a Markdown file is found, the page shows:

```text
custom Markdown
---
automatically generated descriptor reference
```

When no file is found, the page contains only the automatically generated
reference. Missing documentation never prevents a tool or plugin from loading.

Custom Markdown can link to another tool with:

```markdown
[Related tool](tool:other.tool)
```

It can link to the host documentation root with:

```markdown
[File formats](opentools-doc:file-formats.md)
```

Current custom tool documents support Markdown text and these navigation links.
Images embedded in plugin documentation are not yet exposed through a plugin
document URL and should not be used. Keep documentation UTF-8 and below 256 KiB
per file. A plugin may provide at most 512 tool documents and 16 MiB total;
invalid paths, UTF-8, or oversized files produce plugin diagnostics.

## Maven project layout

```text
my-plugin/
  pom.xml
  src/
    main/
      java/
        com/example/plugin/MyPlugin.java
      resources/
        META-INF/opentools-plugin.json
        META-INF/opentools/i18n/en_US.properties
        META-INF/opentools/i18n/zh_CN.properties
```

A minimal `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.example</groupId>
  <artifactId>my-opentools-plugin</artifactId>
  <version>1.0.0</version>

  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.opentools</groupId>
      <artifactId>opentools-plugin-api</artifactId>
      <version>0.11.0-SNAPSHOT</version>
      <scope>provided</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.6.0</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals>
              <goal>shade</goal>
            </goals>
            <configuration>
              <finalName>my-plugin</finalName>
              <createDependencyReducedPom>false</createDependencyReducedPom>
              <filters>
                <filter>
                  <artifact>*:*</artifact>
                  <excludes>
                    <exclude>META-INF/*.SF</exclude>
                    <exclude>META-INF/*.DSA</exclude>
                    <exclude>META-INF/*.RSA</exclude>
                  </excludes>
                </filter>
              </filters>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

The language and manifest files belong under `src/main/resources`, so Maven
copies them into the plugin JAR. The package command is:

```powershell
mvn clean package
```

The deliverable is:

```text
target/my-plugin.jar
```

If the plugin has no third-party runtime dependencies, the normal Maven JAR
build is sufficient. The Shade example is for producing one self-contained
plugin JAR.

### Third-party libraries

The plugin API uses `provided` scope and must not be bundled. Other compile
dependencies must be present at runtime in one of these forms:

- merged into the plugin JAR by Shade, with classes at normal package paths;
- loaded from another OpenTools plugin JAR declared in `dependencies`.

Nested JARs such as `lib/foo.jar` or `BOOT-INF/lib/foo.jar` are not unpacked
automatically. If Shade is used, review service files, native libraries,
signatures, and package collisions.

## Install and enable

Put the final JAR directly in the application's `plugins/` directory:

```text
plugins/
  my-plugin.jar
  plugin-state.json
```

Then inspect and enable it:

```powershell
OpenToolsCLI.exe plugins inspect my.plugin
OpenToolsCLI.exe plugins enable my.plugin
OpenToolsCLI.exe plugins list
```

On the portable Linux/macOS distribution, use the `OpenToolsCLI` launcher.
New third-party plugins are disabled until explicitly enabled. The bundled
example plugin is enabled by the release `plugin-state.json`.

## Lifecycle and diagnostics

The host loads the manifest, checks the API declaration and capabilities,
loads the plugin class, binds its localizer, and calls `onEnable`. Registered
tools and pages are removed on disable or failure. Interactive panels must
stop timers in `close()`.

If the main class cannot load, the descriptor is invalid, or `onEnable` throws
an exception, the Plugins page marks that plugin failed with a diagnostic.
Localization problems are warnings and normally leave the plugin enabled.

## Common mistakes

- Putting `opentools-plugin.json` somewhere other than the JAR root.
- Packing `opentools-plugin-api` into the plugin instead of using `provided`.
- Adding a Maven dependency without shading it into the JAR.
- Embedding a dependency as a nested JAR.
- Expecting resource keys to be prefixed automatically.
- Putting plugin language files in the host `lang/` directory.
- Using a tool or page key that does not match the ID used in Java.
- Creating a page object immediately instead of passing a page factory.

## Security

Java plugins run in the OpenTools JVM. Class-loader isolation is not a security
sandbox. Enable only plugins whose source, dependencies, capabilities, and
licenses you trust.

Before publishing, test the JAR in a clean release package without Maven
compile-time dependencies. Confirm the manifest, language files, third-party
classes, enable/disable behavior, tool submission, result output, and license
notices.
