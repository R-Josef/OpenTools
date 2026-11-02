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
  "nameKey": "plugin.name",
  "descriptionKey": "plugin.description",
  "dependencies": [],
  "requestedCapabilities": ["page"]
}
```

只需声明实际使用的能力：`tool`、`page` 和 `data`。`nameKey` 与
`descriptionKey` 是可选的清单本地化键，描述文件中的 `name` 和
`description` 是对应后备文本。

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

### 推荐键结构

建议使用完整、可读的键。工具 ID 和资源键按以下结构约定：

```text
ToolDescriptor.id = greeting.tool

tool.greeting.tool.name
tool.greeting.tool.description
tool.greeting.tool.param.name.title
tool.greeting.tool.param.name.description
result.greeting.tool.summary
result.greeting.tool.detail
```

默认示例插件的 Tool ID 是 `plugin.example.coin-runner`，所以完整键以
`tool.plugin.example.coin-runner` 开头。这里的 `plugin.example` 是 Tool ID
本身的一部分，不是宿主自动添加的文本。

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
        "result.greeting.tool.summary",
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

不要把插件私有的 `tool.*`、`result.*` 或 `page.*` 键加入宿主
`lang/` 文件。`category.plugin` 是宿主分类键，不属于插件资源。

非法 UTF-8、重复键、损坏的 Properties 或超限文件会产生插件诊断。宿主跳过
受影响的文件并回退，不会因此停止整个程序。

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
