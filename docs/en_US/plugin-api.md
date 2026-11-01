# Plugin API 1.0

OpenTools plugins compile against one self-contained JAR:

```text
plugin-sdk/opentools-plugin-api-<version>.jar
```

The JAR contains the plugin entry-point interfaces and the public task
contracts used to build headless tools. It does not contain the OpenTools
runtime, SQLite layer, job service, plugin host, or desktop implementation.

Plugin extension types are under `io.opentools.plugin`. Public task contracts
are under `io.opentools.api.tool`, for example `OpenTool`, `ToolDescriptor`,
`ToolRequest`, `TaskContext`, and `TaskResult`. These packages are loaded from
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
- desktop pages through `registerPage`;
- private data and logs through the declared `data` capability.

## Compile dependency

Maven plugin projects should use:

```xml
<dependency>
  <groupId>io.opentools</groupId>
  <artifactId>opentools-plugin-api</artifactId>
  <version>0.8.0-SNAPSHOT</version>
  <scope>provided</scope>
</dependency>
```

For a direct compiler invocation:

```powershell
javac --release 21 `
  -cp "plugin-sdk\opentools-plugin-api-0.8.0-SNAPSHOT.jar" `
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
OpenTools shares the public API and task contracts from the host loader so a
plugin and the host use identical class identities. The plugin JAR should
therefore contain only its own implementation and dependencies that are not
provided by the API.

Java plugins run inside the OpenTools JVM. Class-loader isolation is not a
security sandbox. Install and enable only trusted plugins after reviewing their
source, dependencies, requested capabilities, and licenses.

## Compatibility

Plugin API 1.0 is the only supported extension contract. The major version must
match the host. A plugin may request the current minor version or an older minor
version within the same major version. This release does not support legacy
Java panel plugins, `.plugin` script packages, or TBtools plugin binaries.
