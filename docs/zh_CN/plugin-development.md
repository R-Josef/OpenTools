# 插件开发者指南

OpenTools 插件使用 Plugin API 1.0，通过独立类加载器注册工具和页面。插件 JAR
需要 `META-INF/opentools-plugin.json`。

## 最小描述

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

省略能力时默认包含全部能力；插件应只声明实际需要的 `tool`、`page` 和 `data`。

## 构建

使用仓库示例作为起点：

```powershell
mvn -pl opentools-example-plugin -am package
```

把插件 JAR 放入发布目录的 `plugins/`。复制不会自动执行插件；必须在 GUI 或 CLI
中显式启用。

```powershell
opentools plugins inspect example.plugin
opentools plugins enable example.plugin
opentools plugins list
opentools plugins disable example.plugin
```

## 生命周期与兼容

管理流程包括描述校验、API 兼容检查、依赖拓扑加载、能力检查、启用和失败回滚。
插件私有依赖应放在自己的目录或声明可加载的 JAR，不应依赖宿主未公开类。

## 编译 SDK

通用 JAR 发布目录包含 `plugin-sdk/`：

```text
plugin-sdk/
  opentools-plugin-api-<version>.jar
```

使用 Java 21 编译插件时，将该 JAR 放入编译器 classpath，但不要把它打进插件
JAR。宿主会在运行时提供相同契约。

Maven 插件应使用 `provided` scope 依赖 `opentools-plugin-api`。API JAR
同时包含扩展接口和实现插件工具所需的公共执行契约。

完整编译方式、运行时边界、Javadoc 和兼容策略见
[Plugin API 1.0](plugin-api.md)。

## 安全边界

Java 插件在宿主 JVM 内运行，类加载隔离不是安全沙箱。插件可能访问当前用户权限。
OpenTools 默认禁用插件，但不把不受信任代码变成安全代码。发布者应提供来源、
哈希、依赖和许可证，使用者应在启用前审查。

## 数据与日志

插件只能使用宿主分配的私有数据目录，不写入应用资源或其他插件目录。不要记录
凭据、研究数据全文或 MCP token。插件失败应从共享工具注册表撤销并保留诊断。

## 发布检查

- API 版本和依赖合法；
- 能力最小化；
- 无路径穿越或解压炸弹行为；
- 工具参数有完整 Schema；
- 工具、GUI、CLI、MCP 使用同一执行契约；
- 许可证和第三方声明完整；
- 有禁用、失败回滚和升级测试。
