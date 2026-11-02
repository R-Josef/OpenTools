# 准备 MCScanX 输入

`mcscan.input` · 比较基因组 · v1.0.0

## 一句话说明

生成 MCScanX .gff 和 .blast 文件。

它完成一个明确、可复现的分析步骤。运行记录会保存规范化参数、输入文件哈希和日志；正式发表前仍应检查原始数据、参数和生物学解释。

## 什么时候使用

- 准备、运行或整理共线性与 Ka/Ks 等比较基因组分析。
- 需要对单一步骤保留参数、日志和输出，便于重复运行或排查问题时。

## 输入准备

- 声明格式：`GFF3/GTF`、`BLAST tabular`
- 必须提供：`annotation`, `blast`
- 确认输入格式、ID、表头和坐标约定与参数描述一致，并保留原始文件。

### 格式示意

**annotation**

```text
##gff-version 3
chr1	OpenTools	gene	1	100	.	+	.	ID=gene1
chr1	OpenTools	mRNA	1	100	.	+	.	ID=tx1;Parent=gene1
```


## 推荐运行步骤

1. 先确认文件格式、序列名/列名和坐标约定一致。
2. 使用默认值或下表中的推荐值运行校验，先排除路径、表头和类型错误。
3. 提交任务后同时检查摘要、结果表和输出文件；不要只看“成功”状态。
4. 如需复现，保留原始输入、参数、运行日志和输出哈希。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `annotation` 注释 | 是 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 包含 gene feature 的 GFF3/GTF。 |
| `blast` BLAST 表格 | 是 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 表格形式 BLAST 命中。 |
| `outputPrefix` 输出前缀 | 否 | mcscan | 可选项通常先留空；填写时保持 ID、列名或语法与输入一致。 .gff 和 .blast 输出的前缀。 |
| `overwrite` 覆盖输出 | 否 | false | 默认 `false`；只有确认目标文件可替换时才设为 `true`。 允许替换已有输出。 |

## 结果怎么读

- 输出格式：`GFF`、`BLAST`
- 生成 `.gff` 与 `.blast` 输入；检查基因 ID、染色体命名、BLAST 方向和文件前缀是否一致。
- 结果面板是快速检查入口；完整数据、运行清单和日志位于本次运行目录。

## 最小运行示例

```powershell
opentools run mcscan.input --param annotation=C:\data\annotation.gff3 --param blast=C:\data\blast.tsv
```

把占位路径替换为工作区中的真实文件。参数名必须保持原样，布尔值使用 `true` 或 `false`。

## 检查清单与失败边界

- 格式、列名、坐标或资源版本不匹配时，任务会在发布输出前失败。
- 已有输出默认不会被覆盖；只有明确设置输出路径并接受覆盖时，才检查 `overwrite`。

## 相关下一步

- [运行 MCScanX](tool:mcscan.run)
- [本地 BLAST](tool:alignment.blast.local)

[返回文档首页](opentools-doc:README.md)
