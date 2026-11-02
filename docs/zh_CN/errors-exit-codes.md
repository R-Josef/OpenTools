# 错误与退出码

## CLI 退出码

| 退出码 | 含义 | 典型情况 |
| --- | --- | --- |
| `0` | 成功或已排队 | 运行成功、`--no-wait` 已提交 |
| `2` | 输入或参数错误 | 文件不存在、未知工具、Schema 校验失败 |
| `3` | 外部依赖错误 | 外部工具缺失、版本检查或启动失败 |
| `4` | 执行失败 | 数据格式错误、工具执行异常、部分完成 |
| `5` | 取消 | 作业或外部进程被取消 |
| `6` | 内部错误 | 未分类的宿主异常 |

`--json` 模式在 stdout 输出单一 envelope。失败示例：

```json
{
  "ok": false,
  "apiVersion": "1.0",
  "data": null,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Input FASTA is required.",
    "suggestion": "Choose an existing file.",
    "issues": [],
    "details": {}
  }
}
```

## 常见错误

| 错误码 | 用户动作 |
| --- | --- |
| `INVALID_ARGUMENTS` | 检查命令参数或运行 `--help` |
| `VALIDATION_FAILED` | 按 `issues[].field` 修正输入 |
| `TOOL_NOT_FOUND` | 运行 `tools list` |
| `JOB_NOT_FOUND` | 用 `jobs list` 获取有效 run ID |
| `EXTERNAL_TOOL_MISSING` | 使用便携包、显式路径或环境变量配置工具 |
| `EXTERNAL_PROCESS_START_FAILED` | 检查可执行文件、权限和平台 |
| `PATH_OUTSIDE_WORKSPACE` | 使用项目内路径或添加允许的工作区 |
| `OVERWRITE_NOT_ALLOWED` | 选择新的输出路径 |
| `RESULT_NOT_AVAILABLE` | 等作业进入终态后重试 |
| `DELIMITED_TABLE_MALFORMED` | 检查引号、转义和跨行字段 |
| `TABLE_MEMORY_BOUND` | 降低规模或提高对应的 `max*` 参数 |
| `ANNOTATION_MEMORY_BOUND` | 按序列或类型拆分注释 |
| `ANNOTATION_ACTIVE_BOUND` | 调整 `maxActiveRecords` 或分区 |
| `SPILL_SPACE_LIMIT` | 提高临时空间上限或拆分输入 |
| `SPILL_DISK_FULL` | 释放临时磁盘空间 |
| `FASTQ_QUALITY_INVALID` | 检查质量字符和编码 |
| `GENETIC_CODE_UNSUPPORTED` | 使用 Schema 支持的 NCBI 表代码 |
| `VCF_INDEX_DICTIONARY_REQUIRED` | 添加 `##contig` 序列字典 |
| `VCF_NOT_SORTED` | 按字典顺序排序后创建索引 |
| `CANCELLED` | 需要时重新提交 |
| `INTERNAL_ERROR` | 保留 run log 和诊断信息 |

## MCP 协议错误

- 参数缺失或无效：JSON-RPC `-32602`
- 未知方法：`-32601`
- 无法解析 JSON：`-32700`
- 工具执行错误：`tools/call` 的 `isError=true`，错误详情在
  `structuredContent.error`

## 排查顺序

1. 阅读 `message` 和 `suggestion`。
2. 检查 `issues[].field` 对应的 UI 参数或 CLI `--param`。
3. 对外部工具运行 Environment Check。
4. 打开作业目录中的 `manifest.json` 和 `run.log`。
5. 仍无法分类时保留错误码、工具 ID、版本和日志。
