# Plugin API 2.1

OpenTools 插件只需要针对一个自包含 JAR 编译：

```text
plugin-sdk/opentools-plugin-api-<version>.jar
```

该 JAR 包含插件入口接口和编写无界工具所需的公共执行契约，不包含 OpenTools
运行时、SQLite、作业服务、插件宿主或桌面界面实现。

插件扩展类型位于 `io.opentools.plugin`；公共执行契约位于
`io.opentools.api.tool`，例如 `OpenTool`、`ToolDescriptor`、`ToolRequest`、
`ToolExecutionContext` 和 `ToolResult`。交互工具另外提供
`InteractiveOpenTool`、`ToolPanel` 和 `ToolUiContext`。这些包由宿主在运行时
提供，插件内不应重复打包。

## 扩展契约

插件 JAR 包含自身类和描述文件：

```text
META-INF/opentools-plugin.json
```

描述文件指向实现 `io.opentools.plugin.OpenToolsPlugin` 的主类。插件启用时，
宿主创建该类的实例并把 `PluginContext` 传给 `onEnable`。上下文可以注册：

- 通过 `registerTool` 注册无界面工具；
- 通过注册 `InteractiveOpenTool` 提供自定义交互页面的工具；
- 通过 `registerPage` 注册桌面页面；
- 在声明 `data` 能力后使用插件私有目录和日志。

## 插件本地化

插件私有文案保存在插件 JAR 内，使用 UTF-8 Properties 文件和固定目录：

```text
META-INF/opentools/i18n/en_US.properties
META-INF/opentools/i18n/zh_CN.properties
```

插件 JAR 加载时，宿主一次性为其中每个已启用插件绑定独立 localizer。注册工具时，
宿主把准确的 Tool ID 映射到注册它的插件，不通过 ID 前缀猜测归属。因此两个插件
可以使用完全相同的资源键而不会冲突。

页面和交互面板应通过宿主上下文解析文本：

```java
context.registerPage(
        "main",
        "page.main.title",
        "示例插件",
        () -> page(context)
);
String title = context.text("page.main.title", "示例插件");
String label = toolUiContext.text("game.playerName", "玩家名称");
String summary = context.text(
        "result.example.summary",
        "已完成 {0} 行",
        42
);
```

查找顺序为：

1. 当前完整语言代码，例如 `zh_CN`；
2. 当前语言，例如 `zh`；
3. `en_US`；
4. 调用点传入的 fallback 文本。

提供参数时使用 `java.text.MessageFormat`。localizer 在每次查找时读取当前语言，
因此切换语言后，插件名称与描述、页面标题、工具目录、参数表单、工具文档、交互
页面和结果摘要都会更新，无需重启进程。

`ToolDescriptor` 和 `ToolParameter` 继续保存英文显示文本，在语言文件或键缺失时
作为最终后备。插件清单可以用可选键本地化名称和描述：

```json
{
  "name": "示例插件",
  "nameKey": "plugin.name",
  "description": "示例扩展",
  "descriptionKey": "plugin.description"
}
```

不要把插件私有的 `tool.*`、`result.*` 或 `page.*` 键加入宿主 `lang/` 目录。
宿主分类键仍属于应用语言文件，包括 `category.plugin`。

加载器会检查 UTF-8、重复键、非法 Properties、文件数量、单文件和总计大小、
键数量以及键值长度。问题会生成结构化插件诊断，跳过有问题的资源，同时保持启动
和其他插件可用。资源查找只能访问所属插件 JAR 中
`META-INF/opentools/i18n/` 下一层的文件，不能访问 JAR 外文件。

## 交互工具

`OpenTool` 始终保持无界面契约。需要自定义 Swing 页面的工具实现
`InteractiveOpenTool`，并在 `createPanel(ToolUiContext)` 中返回新的
`ToolPanel`。

交互页面替代宿主自动生成的参数表单。页面可以使用计时器和键盘事件，但完成后的
数据必须通过 `ToolUiContext.submit(ToolRequest)` 提交。这样参数校验、作业、
历史、结果、CLI、MCP 和重试继续共用同一执行契约。

`ToolParameter` 仍然只表示可序列化数据，例如字符串、数字、文件和枚举。不要把
`JPanel` 或其他活动 Swing 对象放进 `ToolDescriptor`、`ToolParameter` 或
`ToolRequest`。

切换工具、切换项目或卸载所属插件时，宿主会关闭 `ToolPanel`。使用计时器的页面
必须在 `close()` 中停止计时器。

## 编译依赖

Maven 插件项目应使用：

```xml
<dependency>
  <groupId>io.opentools</groupId>
  <artifactId>opentools-plugin-api</artifactId>
  <version>0.8.1-SNAPSHOT</version>
  <scope>provided</scope>
</dependency>
```

直接调用编译器时：

```powershell
javac --release 21 `
  -cp "plugin-sdk\opentools-plugin-api-0.8.1-SNAPSHOT.jar" `
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
公共 API 和执行契约，确保宿主和插件看到相同的类身份。因此插件 JAR 只需要包含
自身实现和并非由 API 提供的依赖。

Java 插件运行在 OpenTools JVM 内，类加载隔离不是安全沙箱。只应安装并启用经过
来源、源码、依赖、能力和许可证审查的可信插件。

## 兼容性

Plugin API 2.1 是当前插件扩展契约。主版本必须与宿主一致；插件可以
请求同主版本中的当前或较旧次版本。本版本不支持旧 Java 面板插件、`.plugin`
脚本包或 TBtools 插件二进制。

API 2.1 延续 API 2.0 引入的统一 Tool 语义：

| API 1.0 | API 2.0 |
| --- | --- |
| `TaskContext` | `ToolExecutionContext` |
| `TaskResult` | `ToolResult` |
| `TaskExecutionService` | `ToolExecutionService` |
| `TaskAutomationService` | `ToolAutomationService` |

针对旧契约编译的插件需要使用 2.1 API 重新编译。本版本不提供本地化 deprecated
别名，也不保留宿主与插件双路径语言查找。
