# 检查基因组与注释匹配

`gxf.genome.match` · 基因组注释 · v1.0.0

## 一句话说明

比较注释中的序列 ID、坐标与基因组 FASTA。

它完成一个明确、可复现的分析步骤。运行记录会保存规范化参数、输入文件哈希和日志；正式发表前仍应检查原始数据、参数和生物学解释。

## 什么时候使用

- 检查或改写基因组注释，并验证序列名、坐标和 feature 关系。
- 需要对单一步骤保留参数、日志和输出，便于重复运行或排查问题时。

## 输入准备

- 声明格式：`FASTA`、`FASTA.gz`、`GFF3`、`GTF`
- 必须提供：`genome`, `annotation`
- 确认序列名与基因组一致；GFF3/GTF 坐标按 1-based inclusive 理解，BED 按 0-based half-open 理解。

### 格式示意

**genome**

```text
>seq1
ATGCGTACGT
>seq2
ATGCGTTCGT
```


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
| `genome` 基因组 FASTA | 是 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 注释对应的参考基因组。 |
| `annotation` GFF3/GTF | 是 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 要校验的注释。 |
| `report` 报告 TSV | 否 | 空 | 可留空交给运行目录；自定义输出请确认父目录和扩展名。 可选的不匹配报告路径。 |

## 结果怎么读

- 输出格式：`TSV`
- 不匹配报告会列出序列名、长度或坐标问题；先修复注释与基因组版本不一致，再进行区间分析。
- 结果面板是快速检查入口；完整数据、运行清单和日志位于本次运行目录。

## 最小运行示例

```powershell
opentools run gxf.genome.match --param genome=C:\data\genome.fasta --param annotation=C:\data\annotation.gff3
```

把占位路径替换为工作区中的真实文件。参数名必须保持原样，布尔值使用 `true` 或 `false`。

## 检查清单与失败边界

- 未知序列、反向坐标、超出基因组长度和父子 feature 缺失都应在下游解释前检查。
- 已有输出默认不会被覆盖；只有明确设置输出路径并接受覆盖时，才检查 `overwrite`。

## 相关下一步

- [检查 GFF3/GTF](tool:gxf.inspect)
- [注释检查与修复](tool:gxf.check-repair)
- [按 GFF3/GTF 提取序列](tool:gxf.sequence.extract)

[返回文档首页](opentools-doc:README.md)
