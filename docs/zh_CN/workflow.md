# 工作流

通过 CLI 或 MCP 运行按依赖排序的 JSON 工作流。每个步骤都是普通 OpenTools 任务，因此校验、工作区策略、作业、日志和结果清单全部沿用。成功步骤的产物可以传给后续步骤。

## 工作流文件

```json
{
  "name": "fasta-summary-and-preview",
  "continueOnError": false,
  "steps": [
    {
      "id": "stats",
      "toolId": "fasta.stats",
      "parameters": {"input": "data/sample.fa"}
    },
    {
      "id": "preview",
      "toolId": "table.preview",
      "parameters": {"input": "${stats.output}"},
      "dependsOn": ["stats"]
    }
  ]
}
```

- `id` 必填且在同一工作流内唯一。
- `dependsOn` 可选；省略或空列表表示可以立即运行。
- `parameters` 的参数 ID 与取值类型与 `tools describe <tool-id>` 一致。
- 相对路径按项目根解析；工作区策略仍会拒绝项目或已配置根之外的路径。

## 占位符

| 占位符 | 含义 |
| --- | --- |
| `${step.output}` | 该步骤产生的第一个产物路径 |
| `${step.output[0]}` | 按零基下标选择产物 |
| `${step.metric.KEY}` | 该步骤结果指标中的数值或文本 |
| `${step.runId}` | 被引用步骤的运行 ID |

占位符只在被引用步骤成功后可用。步骤不存在、依赖失败、产物或指标缺失时，该步骤以结构化错误结束。

## 执行规则

- 步骤按拓扑顺序运行；循环依赖和未知依赖会在提交任何作业前被拒绝。
- 单个工作流最多 100 个步骤，每个步骤的自动化超时为一小时。
- `continueOnError=false`（默认）时首个失败即停止，后续步骤标记为 `SKIPPED`。
- `continueOnError=true` 时独立步骤继续；依赖失败步骤的步骤会被跳过。
- 每个步骤返回 `status`、`runId`、`outputs`、`metrics` 和 `errorCode`，部分完成会被如实报告。

## CLI

```powershell
opentools --project C:\work\project workflow run C:\work\project\workflow.json --json
```

JSON 响应包含逐步骤状态和产物。步骤失败退出码为 4；工作流结构错误按错误类型返回 2 或 4。

## MCP

使用 `opentools_run_workflow` 工具，参数为工作区内的路径：

```json
{"path": "workflow.json"}
```

MCP 返回相同的逐步骤结构。需要自定义调度逻辑时，可以继续逐个提交作业。

## GUI 编辑器

在主界面打开 **工作流**，可以用同一个 JSON 格式编辑，并实时预览 DAG：

- **新建**：载入两步模板。
- DAG 画布可直接编辑：点击节点选中、拖动节点调整位置，并用**添加步骤**、
  **编辑步骤**、**删除步骤**修改工作流；步骤对话框可编辑 ID、工具 ID、依赖和参数。
- **校验**：解析 JSON，检查重复 ID、缺失依赖和循环，然后重绘节点图。
- 可视化编辑与 JSON 编辑保持同步；JSON 仍是唯一来源。
- **保存**：写入当前项目的 `workflows/<name>.json`，左侧列表会显示已保存文件。
- **运行**：按顺序执行步骤，占位符、失败策略、参数校验和作业记录与 CLI/MCP 一致。
- 运行日志和节点颜色显示 `SUCCEEDED`、`FAILED`、`SKIPPED`。

GUI 运行器有意采用顺序执行；脚本化或远程执行请使用 CLI/MCP。JSON 始终是
可移植的唯一来源。

## 目录监听与计划任务

两个功能都是**需要显式启用的 CLI 命令**；OpenTools 不会自动启动监听器或调度器。

### 监听目录

```powershell
opentools --project C:\work\project workflow watch C:\work\incoming `
  --workflow workflows\import.json --pattern "*.fa" --max-runs 1
```

- 工作流参数可以使用 `${watch.file}` 和 `${watch.name}`；监听器在运行前
  替换为匹配到的文件。
- 文件名用 Java glob 匹配（默认 `*.fa`）。
- 文件大小在 debounce 时间内稳定后才会触发。
- `--max-runs 0`（默认）持续监听；`1` 适合测试或一次性投放目录。
- 监听目录和工作流路径都必须位于允许的工作区内。

### 定时运行

```powershell
opentools --project C:\work\project workflow schedule `
  --workflow workflows\nightly.json --every-seconds 3600 --max-runs 1
```

- 默认立即运行一次；`--start-immediately=false` 先等待一个间隔。
- `--max-runs 0` 持续运行，直到中断。
- 没有内置 cron 解析；日历规则可用操作系统计划任务，或短间隔加工作流内
  日期判断。

### 运维注意

- 工作流失败会返回失败步骤汇总；监听器默认继续处理下一个文件，除非进程退出。
- 监听器处理 create/modify 事件，忽略目录和不匹配的文件名。
- 命令运行期间会持有项目和运行时；不要对同一输出路径启动两个调度器。

