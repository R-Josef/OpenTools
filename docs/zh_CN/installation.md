# 安装与运行

## Windows x64 便携整合包

发布目录应保持：

```text
opentools-<version>-windows-x64/
  OpenTools.exe
  OpenToolsCLI.exe
  app/
  runtime/
  tools/
  docs/
    en_US/
    zh_CN/
  licenses/
  THIRD_PARTY_NOTICES.md
  README.txt
```

解压后双击 `OpenTools.exe`；终端、批处理和 MCP 使用 `OpenToolsCLI.exe`。
不需要安装 Java、conda 或 MSYS2；程序优先查找包内 `tools/`。

不要从只读压缩包内直接运行，也不要把 `OpenTools.exe` 单独复制到其他目录。
若 Windows 显示未知发布者，这是当前未签名便携包的限制，不应绕过组织安全策略。

## Linux x64 便携包

完整解压 `opentools-<version>-linux-x64.tar.gz`。图形界面使用
`bin/OpenTools`，终端使用 `bin/OpenToolsCLI`：

```bash
tar -xzf opentools-<version>-linux-x64.tar.gz
cd opentools-<version>-linux-x64
bin/OpenToolsCLI --version
bin/OpenTools
```

归档自带 Java 运行时和 Linux 工具。`app/`、`lib/`、`tools/`、`docs/` 和
`licenses/` 应保持在一起。

## macOS 便携包

Intel Mac 使用 `macos-x64`，Apple Silicon 使用 `macos-arm64`。解压 ZIP 后打开
`OpenTools.app`。终端和 MCP：

```bash
OpenTools.app/Contents/MacOS/OpenToolsCLI --version
```

当前快照包未公证。严格执行 Gatekeeper 策略时，应使用组织签名和公证后的构建。

## 通用 JAR

通用 JAR 需要 Java 21。应用、语言和工具资源应保持发布目录结构。外部分析工具
需由用户安装，并通过包内 `tools/`、工具参数、环境变量或 `PATH` 提供。

```powershell
java -jar opentools.jar
java -jar opentools.jar tools list
java -jar opentools.jar doctor --json
```

## 从源码构建

需要 JDK 21，并确保 `JAVA_HOME` 指向 JDK；也可以先配置本机 `PATH`，使
`java`、`mvn` 和 `jpackage` 均可直接调用。

```powershell
$env:JAVA_HOME = '<JDK 21 安装目录>'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean verify
```

主要构建产物：

```text
opentools-app/target/opentools.jar
opentools-app/target/dist/
target/release/opentools-<version>-windows-x64.zip
target/release/opentools-<version>-linux-x64.tar.gz
target/release/opentools-<version>-macos-x64.zip
target/release/opentools-<version>-macos-arm64.zip
target/release/opentools-<version>-corresponding-sources.tar.gz
```

Windows 便携包需要 JDK 21 的 `jpackage`。维护者可先运行
`scripts/package-windows.ps1`；下载和第三方构建只在构建阶段发生。
默认缓存目录是 `.release-cache/windows`，可用 `-CacheDirectory` 指定其他位置。
带标签发布会在 GitHub Actions 的原生 runner 上构建四种归档。
源码任务还会生成一个供所有平台共用的对应源码包。

### 第三方工具源码构建

发布包在构建阶段填充 `tools/`。需要自行编译外部工具时，可以复用同一套脚本：

- **Linux/macOS：** 运行 `scripts/package-linux.sh` 或
  `scripts/package-macos.sh x64|arm64`，脚本会下载固定版本源码并自动构建
  HMMER、MCScanX、trimAl。要复用已编译好的二进制，传入
  `--native-build-dir /path/to/native`。底层脚本为
  `scripts/build-unix-dependencies.sh`，读取 `HMMER_SOURCE_DIR`、
  `MCSCANX_SOURCE_DIR` 和 `TRIMAL_SOURCE_DIR`。
- **Windows HMMER/MCScanX/trimAl：** 在 MSYS2 中运行
  `scripts/build-msys-dependencies.sh TOOL_ROOT OUTPUT_DIR`。
  `TOOL_ROOT` 需包含 `hmmer-3.4`、`MCScanX-1.0.0` 和
  `trimal-1.5.1/source`。随后用
  `scripts/package-windows.ps1 -NativeBuildDirectory OUTPUT_DIR` 打包。
- **Windows MEME/MAST：** 在 Cygwin 中运行
  `scripts/build-meme-cygwin.sh OUTPUT_DIR`，需安装 `gcc`、`make`、
  `perl` 以及 libxml2/libxslt/gd 开发包。脚本会下载并校验 MEME 5.5.9，
  应用 Cygwin 补丁，生成便携目录。随后用
  `scripts/package-windows.ps1 -MemeDirectory OUTPUT_DIR` 打包。

本地开发时，可把构建产物复制到 `tools/<tool>`，让正常发现顺序找到它；
也可通过显式可执行文件路径、`OPENTOOLS_*` 或 `PATH` 指定。不要把编译产物
提交到 Git。下载缓存位于 `.release-cache/<release>`。BLAST+、DIAMOND、
IQ-TREE 和 MUSCLE 当前使用固定版本的上游二进制；如需源码构建，需自行编译，
并同步修改对应 manifest 和许可证说明。运行 `scripts/package-sources.sh`
可生成发布所用的独立对应源码包。

## 升级

1. 退出 OpenTools，确认没有运行中的作业。
2. 备份项目和自定义插件。项目通常位于用户选择的目录，不随应用升级删除。
3. 解压新版本到新目录，不要覆盖仍在使用中的旧目录。
4. 先启动新版本检查项目和插件兼容性，再删除旧版本。
5. 外部工具升级后重新运行 Environment Check。

语言文件位于应用目录的 `lang/`。自定义翻译应在升级前保存，并与新版本键集合比较。

## 卸载

便携包没有系统安装步骤。退出应用后可删除解压目录。项目、用户设置和外部工具
目录需要单独决定是否保留；应用不会自动删除它们。
