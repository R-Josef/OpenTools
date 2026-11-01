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

自动化工作区默认是项目根。相对路径按项目根解析；MCP 和批量任务还可显式添加
`--workspace`。

## 参数

每个任务使用 `--param key=value`。参数 ID、类型、默认值和可选值以
`tools describe <id> --schema` 为准。

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

## 批处理

批量文件按顺序执行 JSON 项目。批处理不解析 DAG，也不会自动把前一个作业结果
替换为后一个作业的输入。

```powershell
opentools --project C:\work\project batch run examples\batch\batch.json --json
```

自动模式拒绝覆盖、工作区外路径和未知参数。完整错误语义见
[错误与退出码](errors-exit-codes.md)。
