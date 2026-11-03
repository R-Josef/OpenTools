# OpenTools

[English](README.md) | 简体中文

| | |
| :---: | :---: |
| <img src="assets/branding/opentools-banner.svg" alt="OpenTools - 主要由 DeepSeek、Codex 与 DeepSeek Harness 作为 Agent 生成" width="600"> | <img src="https://img.aigengtu.com/0_preview/meme/2677.webp" alt="DeepSeek 鲸娘表情包" width="140"> |

<p align="center">
  <a href="https://www.deepseek.com/"><img src="https://img.shields.io/badge/Generated_with-DeepSeek-4D6BFE" alt="Generated with DeepSeek"></a>
  <a href="https://openai.com/codex/"><img src="https://img.shields.io/badge/Agent-Codex-111111" alt="Agent: Codex"></a>
  <br>
  <img src="https://img.shields.io/badge/Agent-DeepSeek_Harness-20262E" alt="Agent: DeepSeek Harness">
</p>

OpenTools 是一个本地优先、工具驱动的开源生物信息学工作台。它把常用序列、
注释、表格、比对、表达、富集、HTS 和可视化工具放进同一个可搜索界面，并让
同一套工具定义同时服务于 Windows 桌面、CLI 和 MCP。

项目面向 Windows 科研用户、生物信息分析人员和插件开发者。数据默认留在本机，
不要求登录或上传。OpenTools 自身以 GPL-3.0-or-later 发布；随包分发的第三方
程序保留各自许可证，详见 [第三方声明](THIRD_PARTY_NOTICES.md)。

本项目的大部分内容由 **DeepSeek** 模型生成，**Codex** 与 **DeepSeek Harness**
作为编码 Agent 参与开发；目前由 DeepSeek Harness 接手维护。DeepSeek Harness
只是开发工具，不是运行时依赖，发布包中不包含任何 Agent 或模型代码。

![OpenTools 工具目录](docs/zh_CN/assets/screenshots/tools-catalog.jpg)

## 功能概览

- 93 个内置工具，覆盖 FASTA/FASTQ、GFF3/GTF、BED、表格、本地与远程 BLAST、DIAMOND、
  HMMER、多序列比对、系统发育、比较基因组、表达与富集、SAM/BAM/CRAM、
  VCF/BCF、统计图和交互式结果查看。
- 项目、作业、输入哈希、参数、日志和结果清单统一保存，可重试并复制等价 CLI 命令。
- 作业经过有界队列调度，进度持久化，支持取消，并在异常退出后恢复中断状态。
- GO/KEGG 资源包带版本、条目数和 SHA-256 摘要，一次导入后可按 ID 复用；富集结果
  会记录所用资源版本。
- 按依赖排序的工作流可通过占位符把上游产物传给下游步骤，并支持循环检查和
  停止/继续失败策略。
- 共线性图使用真实的 GFF3/GTF 基因顺序；SSR 挖掘输出完美型微卫星位点；
  引物检查支持 IUPAC 匹配并预测扩增产物。
- MEME 基序发现和 MAST 扫描可对接已安装的 MEME Suite；多物种共线性连接可绘制为
  弦/带状概览。
- GUI、CLI 和 MCP 共用 `ToolDescriptor`、参数校验、工具执行和结果模型。
- 随包默认插件提供可交互的“金币跑酷”工具，结束后的数据会进入正式作业和结果链路。
- 内置 Docs 页面离线渲染用户文档，并为全部 93 个工具动态生成参数、范围、
  示例和失败边界说明。
- Java 原生工具可直接运行；BLAST+、DIAMOND、HMMER、MEME/MAST、MUSCLE、
  trimAl、IQ-TREE 和 MCScanX 通过外部工具适配器调用。

## 快速入口

| 入口 | 说明 |
| --- | --- |
| [下载](#下载) | Windows、Linux、macOS 便携包与校验文件 |
| [应用内 Docs](docs/zh_CN/README.md) | 快速开始、分类工具、格式、CLI/MCP、FAQ |
| [CLI](docs/zh_CN/cli.md) | 工具发现、校验、运行、作业和 JSON 自动化 |
| [MCP](docs/zh_CN/mcp.md) | stdio 与仅限本机的认证 HTTP 接入 |
| [工作流](docs/zh_CN/workflow.md) | 按依赖排序的 JSON 工作流与产物占位符 |
| [插件开发](docs/zh_CN/plugin-development.md) | Plugin API、生命周期和示例 |

## 下载

最新快照 `v0.9.0-SNAPSHOT` 提供四个便携包，每个归档在同一 Release 中都带有
对应的 `.sha256.json` 校验文件。

| 平台 | 安装包 | 校验 |
| --- | --- | --- |
| Windows x64 | [opentools-0.9.0-SNAPSHOT-windows-x64.zip](https://github.com/R-Josef/OpenTools/releases/download/v0.9.0-SNAPSHOT/opentools-0.9.0-SNAPSHOT-windows-x64.zip) | [sha256](https://github.com/R-Josef/OpenTools/releases/download/v0.9.0-SNAPSHOT/opentools-0.9.0-SNAPSHOT-windows-x64.zip.sha256.json) |
| Linux x64 | [opentools-0.9.0-SNAPSHOT-linux-x64.tar.gz](https://github.com/R-Josef/OpenTools/releases/download/v0.9.0-SNAPSHOT/opentools-0.9.0-SNAPSHOT-linux-x64.tar.gz) | [sha256](https://github.com/R-Josef/OpenTools/releases/download/v0.9.0-SNAPSHOT/opentools-0.9.0-SNAPSHOT-linux-x64.tar.gz.sha256.json) |
| macOS x64（Intel） | [opentools-0.9.0-SNAPSHOT-macos-x64.zip](https://github.com/R-Josef/OpenTools/releases/download/v0.9.0-SNAPSHOT/opentools-0.9.0-SNAPSHOT-macos-x64.zip) | [sha256](https://github.com/R-Josef/OpenTools/releases/download/v0.9.0-SNAPSHOT/opentools-0.9.0-SNAPSHOT-macos-x64.zip.sha256.json) |
| macOS arm64（Apple Silicon） | [opentools-0.9.0-SNAPSHOT-macos-arm64.zip](https://github.com/R-Josef/OpenTools/releases/download/v0.9.0-SNAPSHOT/opentools-0.9.0-SNAPSHOT-macos-arm64.zip) | [sha256](https://github.com/R-Josef/OpenTools/releases/download/v0.9.0-SNAPSHOT/opentools-0.9.0-SNAPSHOT-macos-arm64.zip.sha256.json) |

[浏览全部 Release](https://github.com/R-Josef/OpenTools/releases) ·
[最新 Release](https://github.com/R-Josef/OpenTools/releases/latest)

Windows x64、Linux x64、macOS x64 和 macOS arm64 由
`.github/workflows/release.yml` 的平台矩阵构建。安装器、自动更新和 macOS
Developer ID 签名尚未包含。通用 JAR 与源码构建说明见
[发布包说明](docs/zh_CN/release-packages.md)。

## 安装与首次运行

### Windows x64 便携包

1. 下载对应 Windows、Linux、macOS Intel 或 macOS Apple Silicon 的发布包。
2. 完整解压归档。
3. Windows 双击 `OpenTools.exe`，Linux 运行 `bin/OpenTools`，macOS 打开
   `OpenTools.app`。
4. 终端和 MCP 分别使用 `OpenToolsCLI.exe`、`bin/OpenToolsCLI` 或
   `OpenTools.app/Contents/MacOS/OpenToolsCLI`。
5. 首次启动会创建或打开本地项目，可从 Home 打开工具目录或 Docs。

不要只复制单个启动器；运行时、应用和工具目录必须保持在同一发布目录。

### 通用 JAR

需要 JDK 21：

```powershell
java -jar opentools.jar
java -jar opentools.jar tools list
java -jar opentools.jar --project C:\work\project run fasta.stats `
  --param input=C:\data\genome.fa
```

源码构建：

```powershell
mvn clean verify
```

完整安装、升级和路径说明见 [安装与升级](docs/zh_CN/installation.md)。

## 外部工具

便携包优先从自身 `tools/` 目录发现工具。开发者版还可使用：

- 工具中的显式可执行文件或目录参数；
- `OPENTOOLS_BLASTN`、`OPENTOOLS_BLASTP`、`OPENTOOLS_MAKEBLASTDB`、
  `OPENTOOLS_DIAMOND`、`OPENTOOLS_HMMSCAN`、`OPENTOOLS_HMMSEARCH`、
  `OPENTOOLS_MUSCLE`、`OPENTOOLS_TRIMAL`、`OPENTOOLS_IQTREE` 和
  `OPENTOOLS_MCSCANX`；
- 系统 `PATH`。

如果外部程序在系统 `PATH` 中可用，OpenTools 也能使用。修改 PATH 后通常需要重启
Explorer 或重新登录，才能让新启动的程序继承更新后的环境。

构建维护者从官方固定地址下载并校验 SHA-256，缓存后可离线复用。终端用户不需要
conda。第三方许可证、源码地址和构建信息见 `licenses/`、
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与
[外部工具说明](docs/zh_CN/external-tools.md)。

## 许可证

OpenTools 使用 [GNU GPL v3.0 or later](LICENSE)。第三方可执行程序作为同一 ZIP
中的独立聚合程序分发，不改变其原有许可证，也不被重新许可为 GPL。发布包保留
逐项许可证、版权、来源、哈希和源码获取方式。具体兼容性与再分发义务仍应由发布
维护者在每次升级时进行人工法律复核。

## 文档

- [文档语言入口](docs/README.md)
- [用户文档首页](docs/zh_CN/README.md)
- [快速开始](docs/zh_CN/quick-start.md)
- [工具参考](docs/zh_CN/tool-reference.md)
- [文件格式](docs/zh_CN/file-formats.md)
- [错误与退出码](docs/zh_CN/errors-exit-codes.md)
- [常见问题](docs/zh_CN/faq.md)
- [第三方开发者文档](docs/zh_CN/plugin-development.md)
- [变更记录](CHANGELOG.md)

应用内 `Docs` 页面读取随 JAR 和便携包一起分发 Markdown。文档渲染完全离线，
不执行 HTML、脚本或远程资源；外部链接只会交给系统浏览器。Docs 默认跟随当前
界面语言，中文读取 `docs/zh_CN`，英文读取 `docs/en_US`；设置页切换语言后立即
重建界面，不需要重启进程。
