# 任务参考

OpenTools `0.8.1-SNAPSHOT` 注册 86 个内置任务。应用内 Docs 的左侧目录列出全部
任务；每个任务页从当前 `ToolDescriptor` 和语言文件生成：

- 用途与适用场景；
- 输入和输出格式；
- 参数标题、ID、类型、默认值、选项和范围；
- 可复制的 CLI 示例；
- 分类相关的注意事项和失败边界。

## 任务类别

| 类别 | 入口 | 任务数 |
| --- | --- | --- |
| 序列处理 | [Sequence](categories/sequence.md) | 13 |
| 注释与 BED | [Genome annotation / Annotation](categories/annotation-bed.md) | 15 |
| 表格 | [Table](categories/table.md) | 8 |
| 比对与系统发育 | [Alignment / Phylogeny / Comparative genomics](categories/alignment-phylogeny.md) | 17 |
| 表达与富集 | [Expression / Enrichment](categories/expression-enrichment.md) | 7 |
| HTS | [HTS](categories/hts.md) | 6 |
| 可视化 | [Visualization](categories/visualization.md) | 20 |

任务按注册表实时生成，以上总数由应用内任务覆盖测试校验。CLI 可使用：

```powershell
opentools tools list --json
opentools tools describe <task-id> --schema
```

若插件增加任务，它们会出现在任务目录中，但不计入 86 个内置任务基线。
