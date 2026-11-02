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
  "nameKey": "plugin.name",
  "descriptionKey": "plugin.description",
  "dependencies": [],
  "requestedCapabilities": ["page"]
}
```

Declare only the capabilities you use: `tool`, `page`, and `data`.
`nameKey` and `descriptionKey` are optional keys for the manifest fallback
text.

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

### Recommended key conventions

Use complete, readable keys. Tool IDs and their resource keys follow these
conventions:

```text
ToolDescriptor.id = greeting.tool

tool.greeting.tool.name
tool.greeting.tool.description
tool.greeting.tool.param.name.title
tool.greeting.tool.param.name.description
result.greeting.tool.summary
result.greeting.tool.detail
```

The default example plugin uses a Tool ID of
`plugin.example.coin-runner`, so its complete keys begin with
`tool.plugin.example.coin-runner`. The `plugin.example` portion is part of the
Tool ID, not text automatically added by the host.

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
        "result.greeting.tool.summary",
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

Do not put plugin-private `tool.*`, `result.*`, or `page.*` keys into the host
`lang/` directory. The host category `category.plugin` is application-owned.

Malformed UTF-8, duplicate keys, broken Properties, and oversized resources
produce plugin diagnostics. The affected file is skipped and the plugin falls
back instead of stopping the whole application.

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
      <version>0.8.1-SNAPSHOT</version>
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
