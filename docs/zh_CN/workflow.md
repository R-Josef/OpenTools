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

## 当前范围

GUI 尚未提供工作流编辑器或 DAG 画布。工作流以 JSON 编写，通过 CLI 或 MCP 执行；应用内 Docs 链接到本指南。
