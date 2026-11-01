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

## Linux x64 便携包

```text
opentools-<version>-linux-x64/
  bin/
    OpenTools
    OpenToolsCLI
  app/
  lib/runtime/
  tools/
  docs/
  licenses/
```

完整解压后运行 `bin/OpenTools` 或 `bin/OpenToolsCLI`。

## macOS 便携包

分别提供 `macos-x64` 和 `macos-arm64` 归档，归档内包含完整的
`OpenTools.app`。GUI 入口是 `Contents/MacOS/OpenTools`，终端和 MCP 使用
`Contents/MacOS/OpenToolsCLI`。

## 平台政策

Windows x64、Linux x64、macOS Intel 和 macOS Apple Silicon 使用独立清单、独立
工具集，并在各自的 GitHub Actions 原生 runner 上构建。任何发布包都不混装其他
平台二进制。

## 构建发布归档

每个平台必须使用对应的原生系统或 runner。首先运行：

```bash
mvn clean verify
```

Windows x64：

```powershell
./scripts/package-windows.ps1
```

Windows 包使用 JDK 21 `jpackage`。CI 会安装带 `make` 和 `gcc` 的 MSYS2；本地维护者
可通过 `-NativeBuildDirectory` 复用已编译的 HMMER、trimAl 和 MCScanX。

Linux x64：

```bash
sudo apt-get install -y autoconf automake build-essential file jq \
  libbz2-dev libcurl4-openssl-dev liblzma-dev libtool unzip zlib1g-dev
export JAVA_HOME=/path/to/jdk-21
./scripts/package-linux.sh
```

macOS Intel 或 Apple Silicon：

```bash
brew install jq
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./scripts/package-macos.sh x64
./scripts/package-macos.sh arm64
```

只能在对应架构的 Mac 上运行相应命令。打包脚本会下载并校验固定版本工具，从源码
构建 HMMER、trimAl、MCScanX，执行包内 CLI 环境检查，最后把归档和校验 JSON 写入
`target/release/`。

Unix 打包脚本通用参数：

```text
--cache-dir PATH
--native-build-dir PATH
--output-dir PATH
--offline
--skip-build
--keep-work
```

`--offline` 要求所有归档都已缓存；`--skip-build` 复用
`opentools-app/target/opentools.jar`；`--keep-work` 保留
`target/release-work-<release>/` 暂存目录。

`Build release packages` GitHub Actions 工作流可手动启动，用于验证四种构建且不会
发布 Release。推送版本标签后会构建并验证全部平台，再创建或更新对应 GitHub
Release，上传所有归档和校验 JSON。

## 签名与安装器

当前便携归档没有安装器或自动更新。Windows 和 Linux 归档未签名；macOS 快照包使用
临时签名且未公证。Developer ID 签名与公证需要独立的发布凭据。

## 可复现信息

每个第三方工具在 pinned manifest 中固定版本、官方 URL、SHA-256 或 NCBI 官方 MD5、
目标目录、许可证
和源码 URL。构建脚本支持本地缓存和离线复用，哈希不符立即终止。发布包附带
`licenses/tools/`、`licenses/java-dependencies/`、来源清单和源码获取说明。
