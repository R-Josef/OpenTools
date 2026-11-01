# Plugin API 1.0

OpenTools 插件只需要针对一个自包含 JAR 编译：

```text
plugin-sdk/opentools-plugin-api-<version>.jar
```

该 JAR 包含插件入口接口和编写无界任务所需的公共任务契约，不包含 OpenTools
运行时、SQLite、作业服务、插件宿主或桌面界面实现。

插件扩展类型位于 `io.opentools.plugin`；公共任务契约位于
`io.opentools.api.tool`，例如 `OpenTool`、`ToolDescriptor`、`ToolRequest`、
`TaskContext` 和 `TaskResult`。这些包由宿主在运行时提供，插件内不应重复打包。

## 扩展契约

插件 JAR 包含自身类和描述文件：

```text
META-INF/opentools-plugin.json
```

描述文件指向实现 `io.opentools.plugin.OpenToolsPlugin` 的主类。插件启用时，
宿主创建该类的实例并把 `PluginContext` 传给 `onEnable`。上下文可以注册：

- 通过 `registerTool` 注册无界面任务；
- 通过 `registerPage` 注册桌面页面；
- 在声明 `data` 能力后使用插件私有目录和日志。

## 编译依赖

Maven 插件项目应使用：

```xml
<dependency>
  <groupId>io.opentools</groupId>
  <artifactId>opentools-plugin-api</artifactId>
  <version>0.8.0-SNAPSHOT</version>
  <scope>provided</scope>
</dependency>
```

直接调用编译器时：

```powershell
javac --release 21 `
  -cp "plugin-sdk\opentools-plugin-api-0.8.0-SNAPSHOT.jar" `
  -d build\classes `
  src\main\java\example\ExamplePlugin.java
```

不要把 API JAR 打包进插件。OpenTools 运行时已经提供相同的 API 类，并通过宿主
类加载器与插件共享。

## Javadoc

编译 SDK 同时提供两种 Javadoc：

```text
plugin-sdk/
  opentools-plugin-api-<version>.jar
  opentools-plugin-api-<version>-javadoc.jar
  javadoc/
    index.html
```

直接打开 `javadoc/index.html` 可离线浏览。IDE 和 Maven 工具可以附加或解析
`-javadoc.jar`。

## 运行时边界

插件的私有类和依赖采用 child-first 类加载；OpenTools 则从宿主类加载器共享
公共 API 和任务契约，确保宿主和插件看到相同的类身份。因此插件 JAR 只需要包含
自身实现和并非由 API 提供的依赖。

Java 插件运行在 OpenTools JVM 内，类加载隔离不是安全沙箱。只应安装并启用经过
来源、源码、依赖、能力和许可证审查的可信插件。

## 兼容性

Plugin API 1.0 是当前唯一支持的插件扩展契约。主版本必须与宿主一致；插件可以
请求同主版本中的当前或较旧次版本。本版本不支持旧 Java 面板插件、`.plugin`
脚本包或 TBtools 插件二进制。
