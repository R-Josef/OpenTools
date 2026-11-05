---
name: opentools-plugin-development
description: Design, implement, package, install, and validate an OpenTools plugin JAR, including tools, plugin pages, localization, and API 2.1 manifest rules. Use when creating or modifying an OpenTools plugin.
---

# OpenTools plugin development

Build a normal OpenTools plugin JAR; do not modify the host module graph for a
third-party plugin.

## Read first

- `docs/en_US/plugin-development.md` for the complete contract.
- `docs/en_US/plugin-api.md` for tool, result, and UI interfaces.
- `opentools-example-plugin/` for a working Maven plugin.

## Deliverable

One self-contained JAR containing:

```text
META-INF/opentools-plugin.json
your/plugin/classes/...
META-INF/opentools/i18n/en_US.properties
META-INF/opentools/i18n/zh_CN.properties   # optional
```

## Workflow

1. Choose a stable plugin ID and tool IDs. Tool IDs are global across the host;
   they are not automatically prefixed with the plugin ID.
2. Create a Maven module with Java 21 and the API as `provided`:
   ```xml
   <dependency>
     <groupId>io.opentools</groupId>
     <artifactId>opentools-plugin-api</artifactId>
     <version>0.11.0-SNAPSHOT</version>
     <scope>provided</scope>
   </dependency>
   ```
3. Add `src/main/resources/META-INF/opentools-plugin.json`:
   ```json
   {
     "id": "com.example.myplugin",
     "name": "My Plugin",
     "version": "1.0.0",
     "apiVersion": "2.1",
     "mainClass": "com.example.plugin.MyPlugin",
     "requestedCapabilities": ["tool", "page"]
   }
   ```
4. Implement `io.opentools.plugin.OpenToolsPlugin` and register in `onEnable`:
   - `context.registerTool(new MyTool())`
   - `context.registerPage("main", "My Plugin", () -> panel(context))`
5. Implement tools with `AbstractOpenTool`: stable `ToolDescriptor`,
   `ToolParameter` definitions, validation, and `execute`. Return
   `ToolResult` with summary, detail, table, artifacts, and metrics.
6. Add localization under `META-INF/opentools/i18n/`. The host derives
   `tool.<id>.name`, `tool.<id>.parameter.<id>.title`, and
   `page.<id>.title`; arbitrary `context.text(...)` keys are literal and
   are not auto-prefixed.
7. Build and inspect:
   ```powershell
   mvn clean package
   jar tf target/my-plugin.jar
   ```
8. Install and test in a clean package:
   ```text
   plugins/my-plugin.jar
   ```
   ```powershell
   OpenToolsCLI.exe plugins inspect com.example.myplugin
   OpenToolsCLI.exe plugins enable com.example.myplugin
   OpenToolsCLI.exe plugins list
   ```
9. Verify disabled/enabled lifecycle, tool validation, successful execution,
   result rendering, both languages, and failure diagnostics.

## Boundaries

- Never bundle `opentools-plugin-api`; it is `provided`.
- Shade runtime dependencies into the JAR or declare another plugin dependency;
  nested JARs such as `lib/*.jar` are not unpacked.
- Do not put plugin resources in the host `lang/` or host `docs/` directories.
- UI panels must release timers, listeners, and threads when closed.
- Plugins run in the host JVM without a security sandbox. Trust the source,
  dependencies, capabilities, and licenses you enable.
- Test the final JAR without Maven compile-time dependencies before publishing.
