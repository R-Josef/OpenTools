# 插件开发者指南

本指南从 OpenTools 实际加载的插件 JAR 开始说明。可以使用 Maven 构建，但最终
交付物仍然是一个放入 `plugins/` 的普通 JAR。

## JAR 中需要包含什么

每个插件 JAR 都必须包含：

```text
META-INF/opentools-plugin.json
your/plugin/classes/...
META-INF/opentools/i18n/en_US.properties   # 推荐提供
META-INF/opentools/i18n/zh_CN.properties   # 可选
```

描述文件说明插件信息。只有页面的插件可以从下面开始：

```json
{
  "id": "my.plugin",
  "name": "我的插件",
  "version": "1.0.0",
  "apiVersion": "2.1",
  "mainClass": "com.example.plugin.MyPlugin",
  "description": "我的 OpenTools 扩展",
  "dependencies": [],
  "requestedCapabilities": ["page"]
}
```

### Manifest 字段

| 字段 | 必需 | 含义 |
| --- | --- | --- |
| `id` | 是 | 插件 ID。只能使用 `A-Z`、`a-z`、`0-9`、`.`、`_`、`-`。 |
| `name` | 是 | 后备显示名称。 |
| `version` | 是 | 插件版本，与 OpenTools 版本无关。 |
| `apiVersion` | 是 | 插件使用的 API 版本，当前为 `2.1`。 |
| `mainClass` | 是 | 实现 `io.opentools.plugin.OpenToolsPlugin` 的类。 |
| `description` | 否 | 后备描述。 |
| `author` | 否 | 作者或维护者信息。 |
| `dependencies` | 否 | 其他 OpenTools 插件 ID，不是 Maven 坐标。 |
| `requestedCapabilities` | 否 | 可包含 `tool`、`page`、`data`；省略时三个能力全部启用。 |

插件显示文本固定使用 `plugin.name` 和 `plugin.description`。Manifest 中的
`name` 与 `description` 仍然是后备文本。

## 最小可用插件

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
                "我的插件",
                () -> mainPage(context)
        );
    }

    private static JPanel mainPage(PluginContext context) {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        panel.add(new JLabel(context.text(
                "page.main.heading",
                "我的插件"
        )));
        return panel;
    }
}
```

`registerPage` 的三个参数分别是：

- `"main"`：插件内部唯一的页面 ID；
- `"我的插件"`：英文或其他语言都缺失时使用的后备标题；
- `() -> mainPage(context)`：用户打开页面时才创建组件的页面工厂。

宿主会根据页面 ID 自动生成标题键：

```text
main -> page.main.title
settings -> page.settings.title
```

只有需要自定义资源键时，才使用四参数 `registerPage`。工具通过
`context.registerTool(new MyTool())` 单独注册。工具契约见
[Plugin API 2.1](plugin-api.md)。

## ID 规则

ID 是运行时契约和资源键契约的一部分。发布插件前应确定，之后尽量保持稳定。

| ID | 作用域 | 用途 |
| --- | --- | --- |
| 插件 ID | 所有已安装插件中唯一 | Manifest、私有数据目录、插件依赖 |
| Tool ID | 宿主工具注册表中唯一 | 注册工具及标准工具文本键 |
| 参数 ID | 单个工具内唯一 | 请求参数表和参数文本键 |
| 页面 ID | 单个插件内唯一 | 注册页面及默认页面标题键 |

重要说明：

- Tool ID 不会自动添加插件 ID 前缀。
- Tool ID 也不会自动添加 `plugin.` 前缀。
- 两个插件不能注册相同 Tool ID，因为宿主工具注册表是全局的。
- 宿主内部会把页面 ID 限定为 `<插件ID>.<页面ID>`，但这个限定值不会自动作为
  页面标题资源键。
- 参数 ID 会成为 JSON 字段名，不应用作可翻译文本，也不要为了本地化重命名。

## 资源发现契约

下表是完整的命名契约。表中只使用占位符，不混入具体 ID 示例。

| 用途 | 契约路径或键 | 所在位置 |
| --- | --- | --- |
| 插件清单 | `META-INF/opentools-plugin.json` | 插件 JAR |
| 本地化文件 | `META-INF/opentools/i18n/<Language>.properties` | 插件 JAR |
| 工具指南 | `META-INF/opentools/docs/<Language>/tool/<Tool ID>.md` | 插件 JAR |
| 插件名称 | `plugin.name` | 本地化文件 |
| 插件描述 | `plugin.description` | 本地化文件 |
| 插件页面标题 | `page.<Page ID>.title` | 本地化文件 |
| 工具名称 | `tool.<Tool ID>.name` | 本地化文件 |
| 工具描述 | `tool.<Tool ID>.description` | 本地化文件 |
| 工具参数标题 | `tool.<Tool ID>.parameter.<Parameter ID>.title` | 本地化文件 |
| 工具参数描述 | `tool.<Tool ID>.parameter.<Parameter ID>.description` | 本地化文件 |
| 结果摘要 | `result.<Tool ID>.summary` | 本地化文件 |
| 结果详情 | `result.<Tool ID>.detail` | 本地化文件 |
| 结果表格列名 | `result.<Tool ID>.column.<Normalized Column>` | 本地化文件 |
| 结果表格值 | `result.<Tool ID>.value.<Normalized Value>` | 本地化文件 |
| 工具分类 | `category.<Category>` | 宿主语言文件 |

文件路径使用 `/`，因为它们是资源路径；本地化键使用 `.`，因为它们是扁平的键
路径。即使 ID 自身包含点号，ID 仍作为一个完整、不可拆分的身份使用。

## 本地化

插件本地化采用固定目录和约定式查找。把 UTF-8 `.properties` 文件放在插件 JAR
中的准确位置：

```text
META-INF/opentools/i18n/en_US.properties
META-INF/opentools/i18n/zh_CN.properties
```

插件启用时由宿主加载。插件作者不需要自己创建 `ResourceBundle`，也不需要逐键
上传到注册表。

### 资源键不会自动改写

这一点很重要：**OpenTools 不会自动给任意键添加
`tool.plugin.<插件ID>` 前缀。**

例如：

```java
context.text("game.start", "开始")
```

查找的是完整键：

```properties
game.start=开始
```

宿主不会再去查找 `tool.plugin.my.plugin.game.start`。

隔离来自 localizer，而不是字符串改写。每个插件可以使用相同的 `game.start`，
因为它的 localizer 只能读取自己 JAR 中的资源。

### 键构造总表

| 宿主显示的内容 | 资源键 | 谁构造 |
| --- | --- | --- |
| 工具名称 | `tool.<Tool ID>.name` | 宿主 |
| 工具描述 | `tool.<Tool ID>.description` | 宿主 |
| 参数标题 | `tool.<Tool ID>.parameter.<参数 ID>.title` | 宿主 |
| 参数描述 | `tool.<Tool ID>.parameter.<参数 ID>.description` | 宿主 |
| 插件页面标题 | `page.<页面 ID>.title` | 宿主 |
| 四参数页面标题 | 插件传入的键 | 插件 |
| 工具分类 | `category.<分类小写>` | 宿主语言文件 |
| 结果表格列名 | `result.<Tool ID>.column.<规范化列名>` | 宿主 |
| 结果表格值 | `result.<Tool ID>.value.<规范化值>` | 宿主 |
| 结果摘要/详情 | `result.<Tool ID>.summary` / `detail` | 宿主 |
| 自定义页面文字 | 传给 `context.text(...)` 的键 | 插件 |

`page.*` 只属于从 Plugins 页面通过“打开插件页面”打开的 Plugin Page。交互工具
页面没有独立页面 ID，其身份就是 Tool ID，标题使用 `tool.<Tool ID>.name`。

分类本地化属于宿主应用。插件工具通常使用 descriptor 分类 `Plugin`，它对应宿主
键 `category.plugin`。把 `category.plugin` 放进插件 JAR 不会改变宿主显示的分类。

结果表格的列名和值会先经过显示用规范化：

```text
转小写
% 变成 " percent"
所有连续的非 a-z/0-9 字符变成 "_"
去掉首尾 "_"
```

例如：

```text
"Distance (m)" -> distance_m
"SECOND_CLICK" -> second_click
```

这只影响显示文本，不会修改结果 JSON 或导出的数据。

### 推荐键结构

建议使用完整、可读的键。ID 应表达数据或操作，不要重复顶层资源类型：

```text
ToolDescriptor.id = greeting
参数 ID = recipient

tool.greeting.name
tool.greeting.description
tool.greeting.parameter.recipient.title
tool.greeting.parameter.recipient.description
result.greeting.summary
result.greeting.detail
```

不要写成 `greeting.tool`；开头的 `tool.` 已经表达了资源类型。参数 ID 不应使用
`name`、`title` 或 `description`，这些是界面字段，不是参数所表达的数据。

默认示例插件使用 Tool ID `example.coin-runner`，所以完整键以
`tool.example.coin-runner` 开头。这里的 `example` 是该 Tool ID 本身的一部分，
不是宿主自动添加的文本。

页面标题使用：

```properties
page.main.title=我的插件
```

清单名称和描述使用：

```properties
plugin.name=我的插件
plugin.description=我的 OpenTools 扩展
```

自己的 Swing 组件可以使用任意键。创建页面时调用
`PluginContext.text(...)`，交互工具内调用 `ToolUiContext.text(...)`：

```java
String start = context.text("game.start", "开始");
String message = context.text(
        "result.greeting.summary",
        "已处理 {0} 行",
        42
);
```

提供参数时使用 `java.text.MessageFormat`。查找顺序为：

```text
当前完整语言 -> 当前语言 -> en_US -> 调用点 fallback
```

没有语言文件时插件仍会加载并显示 fallback。直接写死文本也不会报错，只是不会
随界面语言切换。

### 不会自动本地化的数据

标准文本解析器不会翻译具有执行语义的 descriptor 数据：

- `ToolParameter.options`，因为这些值会用于提交和校验；
- 具有执行语义的 `ToolParameter.defaultValue`；
- `ToolDescriptor.aliases`、输入格式和输出格式；
- JSON 结果和导出文件中的表格行数据。

这些值应保持稳定并使用语言中立的表示。例如执行值继续使用 `SECOND_CLICK`，
只在界面上通过规范化后的结果值键显示本地化文本。

不要把插件私有的 `tool.*`、`result.*` 或 `page.*` 键加入宿主
`lang/` 文件。`category.plugin` 是宿主分类键，不属于插件资源。

非法 UTF-8、重复键、损坏的 Properties 或超限文件会产生插件诊断。宿主跳过
受影响的文件并回退，不会因此停止整个程序。

### 资源文件限制与校验

| 规则 | 限制或格式 |
| --- | --- |
| 编码 | 严格 UTF-8 |
| 位置 | 直接位于 `META-INF/opentools/i18n/` 下 |
| 语言文件名 | `[A-Za-z]{2,8}(?:_[A-Za-z0-9]{2,8})?.properties` |
| 每个插件的语言文件数 | 最多 64 |
| 单个文件 | 最多 256 KiB |
| 全部语言文件 | 最多 1 MiB |
| 单文件键数 | 最多 5,000 |
| 单个键 | 最多 256 个字符 |
| 单个值 | 最多 16,384 个字符 |

推荐提供 `en_US.properties` 作为插件后备语言，但缺少它不影响插件加载。重复键和
损坏的 Properties 会使受影响的整个语言文件被跳过。嵌套语言目录会被忽略并生成
诊断。

提供参数时使用 `MessageFormat`。资源加载阶段不会校验每个消息模板，因此非法模板
可能在该文本实际显示时抛出异常。

## 可选的工具 Markdown

插件可以补充更完整的任务文档，不需要注册 API。宿主在加载插件 JAR 时按约定发现：

```text
META-INF/opentools/docs/<语言>/tool/<Tool ID>.md
```

例如：

```text
Tool ID: example.coin-runner

META-INF/opentools/docs/en_US/tool/example.coin-runner.md
META-INF/opentools/docs/zh_CN/tool/example.coin-runner.md
```

文件名是完整 Tool ID 加 `.md`。参与文档文件查找的 Tool ID 只能使用
`A-Z`、`a-z`、`0-9`、`.`、`_`、`-`。

语言回退顺序为：

```text
当前完整语言 -> 当前语言 -> en_US
```

找到 Markdown 后，页面按以下方式组合：

```text
自定义 Markdown
---
根据 descriptor 自动生成的参考
```

找不到文件时，页面只显示自动生成的参考。缺少文档不会阻止工具或插件加载。

自定义 Markdown 可以使用跨工具链接：

```markdown
[相关工具](tool:other.tool)
```

也可以链接到宿主文档根目录：

```markdown
[文件格式](opentools-doc:file-formats.md)
```

当前自定义工具文档支持 Markdown 文本和上述导航链接。插件文档中的内嵌图片还没有
对应的插件资源 URL，因此暂不支持。文档必须使用 UTF-8，单个文件不超过
256 KiB；每个插件最多提供 512 个工具文档，总计不超过 16 MiB。非法路径、UTF-8
或超限文件会生成插件诊断。

## Maven 项目结构

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

最小 `pom.xml`：

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

语言文件和描述文件放在 `src/main/resources`，Maven 会把它们复制到插件 JAR。
构建命令：

```powershell
mvn clean package
```

交付文件：

```text
target/my-plugin.jar
```

如果插件没有第三方运行时依赖，普通 Maven JAR 构建即可。上面的 Shade 示例用于
生成一个自包含插件 JAR。

### 第三方库

Plugin API 使用 `provided` scope，不应打包进插件。其他编译依赖必须以下列方式
之一出现在运行时：

- 通过 Shade 展开并合并到插件 JAR，class 位于正常包路径；
- 放在另一个 OpenTools 插件 JAR 中，并通过清单 `dependencies` 声明。

`lib/foo.jar` 或 `BOOT-INF/lib/foo.jar` 这类嵌套 JAR 不会被自动解压。使用 Shade
时应检查 service 文件、本地库、签名文件和包名冲突。

## 安装与启用

把最终 JAR 直接放入应用的 `plugins/` 目录：

```text
plugins/
  my-plugin.jar
  plugin-state.json
```

检查并启用插件：

```powershell
OpenToolsCLI.exe plugins inspect my.plugin
OpenToolsCLI.exe plugins enable my.plugin
OpenToolsCLI.exe plugins list
```

Linux/macOS 便携包使用对应的 `OpenToolsCLI` 启动器。新第三方插件默认禁用，
必须显式启用。发布包通过 `plugin-state.json` 预置默认示例插件的启用状态。

## 生命周期与诊断

宿主加载清单、检查 API 声明和能力、加载主类、绑定 localizer，然后调用
`onEnable`。禁用或失败时撤销工具和页面注册。交互页面必须在 `close()` 中停止
计时器。

如果主类无法加载、描述文件非法或 `onEnable` 抛出异常，Plugins 页面会把该插件
标记为失败并显示诊断。本地化资源问题属于警告，通常不会阻止插件启用。

## 常见错误

- 把 `opentools-plugin.json` 放在 JAR 根目录之外。
- 把 `opentools-plugin-api` 打进插件，而不是使用 `provided`。
- Maven 中添加了第三方依赖，但没有 Shade 到最终 JAR。
- 把第三方库作为嵌套 JAR 放入插件。
- 以为资源键会自动添加前缀。
- 把插件语言文件放进宿主 `lang/` 目录。
- 页面或工具资源键与 Java 中使用的 ID 不一致。
- 注册页面时立即创建组件，而不是传入页面工厂。

## 安全边界

Java 插件运行在 OpenTools JVM 内，类加载隔离不是安全沙箱。只应启用经过来源、
源码、依赖、能力和许可证审查的可信插件。

发布前应在没有 Maven 编译依赖的干净发布包中测试 JAR，确认描述文件、语言资源、
第三方 class、启用/禁用、工具提交、结果输出和许可证声明都正常。
