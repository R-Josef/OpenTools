# Plugin developer guide

OpenTools plugins use Plugin API 1.0 and register tools or pages through an
isolated class loader. A plugin JAR needs
`META-INF/opentools-plugin.json`.

## Minimal descriptor

```json
{
  "id": "example.plugin",
  "name": "Example Plugin",
  "version": "1.0.0",
  "apiVersion": "1.0",
  "mainClass": "com.example.ExamplePlugin",
  "description": "Example extension",
  "dependencies": [],
  "requestedCapabilities": ["tool", "page", "data"]
}
```

Declare only the `tool`, `page`, and `data` capabilities the plugin uses.

## Build and enable

```powershell
mvn -pl opentools-example-plugin -am package
```

Put the JAR in the release `plugins/` directory. Copying a JAR does not execute
it. Enable it explicitly:

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
The API JAR contains both extension interfaces and the public task contracts
needed to implement a plugin tool.

See [Plugin API 1.0](plugin-api.md) for the complete compile, runtime, Javadoc,
and compatibility boundary.

## Security boundary

Java plugins run in the host JVM. Class-loader isolation is not a security
sandbox. Enable only trusted code and review its source, dependencies,
capabilities, and license before use.

## Release checklist

- valid API and dependency declarations;
- minimal capabilities;
- traversal-safe archives and no unpacking bomb;
- complete task schemas;
- shared GUI, CLI, MCP contracts;
- disable and rollback coverage;
- complete license and third-party notices.
