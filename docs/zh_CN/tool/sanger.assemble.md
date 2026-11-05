# Sanger 组装

`sanger.assemble` · 序列 · v1.2.0

## 一句话说明

按 overlap 组装 AB1、FASTQ 或 FASTA Sanger reads，可选锚定参考并报告 consensus 证据来源。

它完成一个明确、可复现的分析步骤。运行记录会保存规范化参数、输入文件哈希和日志；正式发表前仍应检查原始数据、参数和生物学解释。

## 什么时候使用

- 在组装、注释、比对或下游分析前检查、转换或整理 FASTA/FASTQ 数据。
- 需要对单一步骤保留参数、日志和输出，便于重复运行或排查问题时。

## 输入准备

- 声明格式：`AB1`、`FASTQ`、`FASTA`
- 必须提供：`reads`
- 确认序列 ID 不重复、FASTA/FASTQ 记录完整；gzip 文件应保持原始记录格式。

## 推荐运行步骤

1. 先确认文件格式、序列名/列名和坐标约定一致。
2. 使用默认值或下表中的推荐值运行校验，先排除路径、表头和类型错误。
3. 提交任务后同时检查摘要、结果表和输出文件；不要只看“成功”状态。
4. 如需复现，保留原始输入、参数、运行日志和输出哈希。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `reads` Read 文件 | 是 | 空 | 应选择多个 read 文件。支持 AB1/FASTQ/FASTA；一个文件可以包含多条 reads。 |
| `reference` 参考 FASTA | 否 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 可选的参考序列，用于锚定 reads 并填充未覆盖位置。 |
| `minOverlap` 最小 overlap | 否 | 30 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 连接 reads 所需的最小 overlap 长度。 |
| `minIdentity` 最小 overlap 一致性 | 否 | 0.85 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 overlap 中匹配碱基的最小比例。 |
| `trimQuality` 末端修剪质量 | 否 | 20 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 末端修剪窗口所需的平均 Phred 质量。 |
| `trimWindow` 末端修剪窗口 | 否 | 8 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 用于修剪低质量末端的滑动窗口。 |
| `minReadLength` 最小 read 长度 | 否 | 50 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 修剪后短于此长度的 reads 会被丢弃。 |
| `trimAdapters` 裁剪 primer/vector | 否 | true | 从默认值开始；只有在理解上方说明后才改变。 从 reads 两端裁剪常见测序引物和可选的自定义 vector 序列。 |
| `adapterFile` Primer/vector FASTA | 否 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 可选 FASTA，提供要从 reads 两端裁剪的 primer 或 vector 序列。 |
| `minAdapterMatch` 最小 primer 匹配 | 否 | 12 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 裁剪 primer/vector 所需的最小匹配碱基数。 |
| `trimVectorEnds` 用参考裁剪 vector 末端 | 否 | true | 从默认值开始；只有在理解上方说明后才改变。 用参考比对去掉 contig 未对齐末端并输出 insert.fa。 |
| `minVectorOverhang` 最小对齐跨度 | 否 | 20 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 保留在 vector 裁剪后 insert 中的最小对齐跨度。 |
| `consensusThreshold` Consensus 阈值 | 否 | 0.6 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 判定可信碱基所需的质量加权比例。 |
| `heterozygousThreshold` 杂合阈值 | 否 | 0.25 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 判定 IUPAC 杂合碱基所需的第二碱基比例。 |
| `minHeterozygousCoverage` 杂合覆盖度 | 否 | 4 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 考虑杂合判定所需的最小覆盖度。 |
| `minIndelSupport` 最小 indel 支持 | 否 | 1 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 展开 consensus 时插入/缺失所需的最小 reads 支持数。 |
| `outputDirectory` 输出目录 | 否 | 空 | 选择可写目录；批量输出前确认磁盘空间和命名冲突。 留空时保存到本次运行目录。 |
| `overwrite` 覆盖输出 | 否 | false | 默认 `false`；只有确认目标文件可替换时才设为 `true`。 允许替换已存在的输出文件。 |

## 结果怎么读

- 输出格式：`FASTA`、`TSV`、`BED`
- 先看 assembly-quality.tsv 和 warnings.tsv，再看 trimming.tsv、reads 表和 assembly.fa 的完整组装；insert.fa 用参考比对裁掉未对齐的 vector 末端；corrected.fa 是参考坐标结果，consensus_with_indels.fa 把支持度达标的插入/缺失展开为最终序列；indels.tsv 与 variants.tsv 记录插入/缺失和杂合位点，provenance.bed 标记 READ_CONSENSUS、HETEROZYGOUS、DELETION、LOW_CONFIDENCE 和 REFERENCE_FILL。不能把参考填充区当作测序证据。
- 结果面板是快速检查入口；完整数据、运行清单和日志位于本次运行目录。

## 最小运行示例

```powershell
opentools run sanger.assemble --param reads=C:\data\reads-1.dat,C:\data\reads-2.dat
```

把占位路径替换为工作区中的真实文件。参数名必须保持原样，布尔值使用 `true` 或 `false`。

## 检查清单与失败边界

- FASTA/FASTQ 记录、ID 和碱基/质量字符必须完整；损坏或截断的记录会使任务失败。
- 已有输出默认不会被覆盖；只有明确设置输出路径并接受覆盖时，才检查 `overwrite`。

## 相关下一步

- [按 ID 提取 FASTA](tool:fasta.extract)
- [FASTA 统计](tool:fasta.stats)
- [ORF 预测](tool:sequence.orf)

[返回文档首页](opentools-doc:README.md)
