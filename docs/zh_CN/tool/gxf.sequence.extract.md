# 按 GFF3/GTF 提取序列

`gxf.sequence.extract` · 基因组注释 · v1.1.0

## 一句话说明

提取 feature、上游、下游或带侧翼的序列。

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
| `genome` 基因组 FASTA | 是 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 序列 ID 与注释一致的参考基因组。 |
| `annotation` GFF3/GTF | 是 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 定义 feature 坐标的注释文件。 |
| `featureType` Feature 类型 | 否 | CDS | 可选项通常先留空；填写时保持 ID、列名或语法与输入一致。 如 CDS、exon、mRNA 或 gene。 |
| `groupAttribute` 分组属性 | 否 | AUTO | 可选项通常先留空；填写时保持 ID、列名或语法与输入一致。 用于合并分段的属性。AUTO 会根据 feature 类型选择默认值。 |
| `mode` 提取模式 | 否 | FEATURE | 优先使用默认模式；只有输入类型或分析目标明确要求时才切换。 FEATURE 按方向拼接分段；UPSTREAM 和 DOWNSTREAM 提取与链方向一致的侧翼。 |
| `upstream` 上游碱基数 | 否 | 0 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 相对于 feature 链方向的上游长度。 |
| `downstream` 下游碱基数 | 否 | 0 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 相对于 feature 链方向的下游长度。 |
| `output` 输出 FASTA | 否 | 空 | 留空时写入运行目录；自定义路径应位于工作区，并按格式填写扩展名。 留空时保存到本次运行目录。 |
| `overwrite` 覆盖输出 | 否 | false | 默认 `false`；只有确认目标文件可替换时才设为 `true`。 允许替换已有的自定义输出文件。 |

## 结果怎么读

- 输出格式：`FASTA`、`TSV`
- FASTA 每条记录对应一个 feature/组，诊断表保留提取状态；负链注释的上游/下游方向必须按链解释。
- 结果面板是快速检查入口；完整数据、运行清单和日志位于本次运行目录。

## 最小运行示例

```powershell
opentools run gxf.sequence.extract --param genome=C:\data\genome.fasta --param annotation=C:\data\annotation.gff3
```

把占位路径替换为工作区中的真实文件。参数名必须保持原样，布尔值使用 `true` 或 `false`。

## 检查清单与失败边界

- 未知序列、反向坐标、超出基因组长度和父子 feature 缺失都应在下游解释前检查。
- 已有输出默认不会被覆盖；只有明确设置输出路径并接受覆盖时，才检查 `overwrite`。

## 相关下一步

- [检查基因组与注释匹配](tool:gxf.genome.match)
- [基因密度滑窗](tool:gxf.gene-density)
- [BED12 区块分析](tool:bed.blocks)

[返回文档首页](opentools-doc:README.md)
