# MEME/MAST 结果表

`annotation.meme.results` · 注释 · v1.0.0

## 一句话说明

将 MEME motif 或 MAST hit 从 XML 转换为可查看的表格。

它完成一个明确、可复现的分析步骤。运行记录会保存规范化参数、输入文件哈希和日志；正式发表前仍应检查原始数据、参数和生物学解释。

## 什么时候使用

- 把外部注释结果规范化为稳定列名，便于筛选、合并和可视化。
- 需要对单一步骤保留参数、日志和输出，便于重复运行或排查问题时。

## 输入准备

- 声明格式：`MEME XML`、`MAST XML`
- 必须提供：`input`
- 确认输入格式、ID、表头和坐标约定与参数描述一致，并保留原始文件。

## 推荐运行步骤

1. 先确认文件格式、序列名/列名和坐标约定一致。
2. 使用默认值或下表中的推荐值运行校验，先排除路径、表头和类型错误。
3. 提交任务后同时检查摘要、结果表和输出文件；不要只看“成功”状态。
4. 如需复现，保留原始输入、参数、运行日志和输出哈希。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `input` 输入 MEME/MAST XML | 是 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 MEME Suite 生成的 meme.xml 或 mast.xml。 |
| `motifOutput` Motif 表 | 否 | 空 | 可留空交给运行目录；自定义输出请确认父目录和扩展名。 留空时保存 meme-motifs.tsv 到本次运行目录。 |
| `hitOutput` Hit 表 | 否 | 空 | 可留空交给运行目录；自定义输出请确认父目录和扩展名。 留空时保存 mast-hits.tsv 到本次运行目录。 |
| `overwrite` 覆盖输出 | 否 | false | 默认 `false`；只有确认目标文件可替换时才设为 `true`。 允许替换已有输出。 |

## 结果怎么读

- 输出格式：`TSV`
- 输出 MEME motif 表和 MAST hit 表；检查 motif ID、命中的 1-based 坐标、链方向和 match 序列。
- 结果面板是快速检查入口；完整数据、运行清单和日志位于本次运行目录。

## 最小运行示例

```powershell
opentools run annotation.meme.results --param input=C:\data\input.tsv
```

把占位路径替换为工作区中的真实文件。参数名必须保持原样，布尔值使用 `true` 或 `false`。

## 检查清单与失败边界

- 格式、列名、坐标或资源版本不匹配时，任务会在发布输出前失败。
- 已有输出默认不会被覆盖；只有明确设置输出路径并接受覆盖时，才检查 `overwrite`。

## 相关下一步

- [导入 eggNOG 注释](tool:annotation.eggnog.import)
- [导入 Pfam 结构域](tool:annotation.pfam.import)

[返回文档首页](opentools-doc:README.md)
