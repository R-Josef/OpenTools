# 发布包说明

## 通用 JAR

适合：

- 已安装 Java 21；
- 只需要 Java 内置任务；
- 已有外部分析工具或自行管理环境；
- CLI、MCP、服务器或开发调试。

不包含平台二进制。外部工具顺序为包内 `tools/`、显式路径、`OPENTOOLS_*` 环境
变量、`PATH`。

发布目录还包含 `plugin-sdk/`，其中提供自包含的 Plugin API 编译 JAR、Javadoc
JAR 和可直接浏览的 HTML。该 API 只用于编译，不应打包进插件。

## Windows x64 便携包

适合：

- Windows 10/11 x64 普通用户；
- 不希望安装 Java、conda、MSYS2；
- 需要固定版本外部工具和可复现环境。

目录：

```text
opentools-<version>-windows-x64/
  OpenTools.exe
  OpenToolsCLI.exe
  app/
  runtime/
  tools/
    blast/
    diamond/
    hmmer/
    muscle/
    trimal/
    iqtree/
    mcscanx/
  docs/
  plugin-sdk/
  licenses/
  THIRD_PARTY_NOTICES.md
  README.txt
```

`OpenTools.exe` 是正式 GUI 入口，`OpenToolsCLI.exe` 是终端与 MCP 入口。
`.bat` 文件仅用于开发调试，不是终端用户依赖。

## 平台政策

当前只发布 Windows x64。Linux/macOS 应使用独立 CI、独立下载清单、独立工具目录和
独立回归结果。Windows ZIP 不混装其他平台二进制。

## 签名与安装器

当前便携 ZIP 未签名，也没有安装器或自动更新。组织环境中如需签名，应走独立发布
流程；签名和安装器未完成不影响便携 ZIP 解压运行。

## 可复现信息

每个第三方工具在 pinned manifest 中固定版本、官方 URL、SHA-256、目标目录、许可证
和源码 URL。构建脚本支持本地缓存和离线复用，哈希不符立即终止。发布包附带
`licenses/tools/`、`licenses/java-dependencies/`、来源清单和源码获取说明。
