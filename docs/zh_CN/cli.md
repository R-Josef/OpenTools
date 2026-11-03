# CLI

便携包使用 `OpenToolsCLI.exe` 以便在终端显示输出；通用 JAR 直接使用 Java
命令。GUI 入口是 `OpenTools.exe`。

```powershell
opentools tools list
opentools tools describe fasta.stats --schema
opentools doctor --json
opentools run fasta.stats --param input=C:\data\genome.fa
```

## 项目与路径

```powershell
opentools --project C:\work\project tools list
opentools --app-home C:\work\opentools-app run fasta.stats `
  --param input=C:\data\genome.fa
```

自动化工作区默认是项目根。相对路径按项目根解析；MCP 和批量作业还可显式添加
`--workspace`。

## 参数

每个工具使用 `--param key=value`。参数 ID、类型、默认值和可选值以
`tools describe <id> --schema` 为准。

`--json` 输出始终是 UTF-8，非 ASCII 字符按 JSON `\uXXXX` 转义，因此即使
Windows 控制台使用旧代码页，PowerShell 的 `ConvertFrom-Json` 和 Python/Node
仍能正确解析。人类可读输出仍使用平台编码。`run` 会等待作业结束并返回稳定
退出码；需要后台执行时，请在常驻进程（GUI 或 MCP）中提交作业。

```powershell
opentools run fasta.filter --param input=in.fa --param minLength=100 `
  --param output=out.fa.gz
```

## 作业

```powershell
opentools jobs list
opentools jobs status <run-id>
opentools jobs logs <run-id>
opentools jobs cancel <run-id>
opentools jobs retry <run-id>
```

`jobs status` 返回 `progress`（0–1）和 `progressMessage`。作业并发上限可通过
`-Dopentools.jobs.maxConcurrent=N` 设置，默认取 CPU 核数的一半（1–4）。异常退出
遗留的作业会在下次打开项目时标记为 `JOB_INTERRUPTED`。

`jobs cancel` 支持跨进程：在共享项目数据库中标记 queued/running 作业，拥有该
作业的进程会在几秒内轮询到请求并写入 `CANCELLED`。

## 批处理

批量文件按顺序执行相互独立的 JSON 项目。后续步骤需要前一步产物时，请使用工作流。

```powershell
opentools --project C:\work\project batch run examples\batch\batch.json --json
```

## 工作流

工作流文件声明步骤、依赖和 `${step.output}` 占位符；格式与占位符规则见
[工作流](workflow.md)。

```powershell
opentools --project C:\work\project workflow run workflow.json --json
```

自动模式拒绝覆盖、工作区外路径和未知参数。完整错误语义见
[错误与退出码](errors-exit-codes.md)。
