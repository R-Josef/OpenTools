# 工具参考

OpenTools `0.8.1-SNAPSHOT` 注册 86 个内置工具。应用内 Docs 的左侧目录列出全部
工具。每个工具页可以先显示按约定发现的 Markdown 指南，然后显示根据当前
`ToolDescriptor` 和语言文件自动生成的参考。自动部分包括：

- 用途与适用场景；
- 输入和输出格式；
- 参数标题、ID、类型、默认值、选项和范围；
- 可复制的 CLI 示例；
- 分类相关的注意事项和失败边界。

宿主指南路径：

```text
docs/<语言>/categories/<分类>/<Tool ID>.md
```

插件指南路径：

```text
META-INF/opentools/docs/<语言>/categories/<分类>/<Tool ID>.md
```

指南会自动发现，不需要文档注册 API。没有指南时只显示自动生成的参考。

## 工具类别

| 类别 | 入口 | 工具数 |
| --- | --- | --- |
| 序列处理 | [Sequence](categories/sequence.md) | 13 |
| 注释与 BED | [Genome annotation / Annotation](categories/annotation-bed.md) | 15 |
| 表格 | [Table](categories/table.md) | 8 |
| 比对与系统发育 | [Alignment / Phylogeny / Comparative genomics](categories/alignment-phylogeny.md) | 17 |
| 表达与富集 | [Expression / Enrichment](categories/expression-enrichment.md) | 7 |
| HTS | [HTS](categories/hts.md) | 6 |
| 可视化 | [Visualization](categories/visualization.md) | 20 |
| 插件 | [插件工具](categories/plugin.md) | 随包提供 |

工具按注册表实时生成，以上总数由应用内工具覆盖测试校验。CLI 可使用：

```powershell
opentools tools list --json
opentools tools describe <tool-id> --schema
```

插件工具及发现的指南会在运行时出现，但不计入 86 个内置工具基线。
