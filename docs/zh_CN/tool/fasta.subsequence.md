# 提取 FASTA 子序列

`fasta.subsequence` · 序列 · v1.1.0

## 一句话说明

按 ID、坐标和链方向提取完整记录或 1-based inclusive 区域。

它完成一个明确、可复现的分析步骤。运行记录会保存规范化参数、输入文件哈希和日志；正式发表前仍应检查原始数据、参数和生物学解释。

## 什么时候使用

- 在组装、注释、比对或下游分析前检查、转换或整理 FASTA/FASTQ 数据。
- 需要对单一步骤保留参数、日志和输出，便于重复运行或排查问题时。

## 输入准备

- 声明格式：`FASTA`、`FASTA.gz`、`Region table`
- 必须提供：`input`
- 确认序列 ID 不重复、FASTA/FASTQ 记录完整；gzip 文件应保持原始记录格式。

### 格式示意

**input**

```text
>seq1
ATGCGTACGT
>seq2
ATGCGTTCGT
```


## 推荐运行步骤

1. 先确认文件格式、序列名/列名和坐标约定一致。
2. 使用默认值或下表中的推荐值运行校验，先排除路径、表头和类型错误。
3. 提交任务后同时检查摘要、结果表和输出文件；不要只看“成功”状态。
4. 如需复现，保留原始输入、参数、运行日志和输出哈希。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `input` 输入 FASTA | 是 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 FASTA 文件，可选用 gzip 压缩。 |
| `regions` 内联区域 | 否 | 空字符串 | 可选项通常先留空；填写时保持 ID、列名或语法与输入一致。 每行一个请求：单独 ID，或 ID 后接 Tab 分隔的 START、END、可选 STRAND 和可选 OUTPUT_ID；坐标从 1 开始并包含终点。 |
| `regionsFile` 区域文件 | 否 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 可选普通或 gzip 压缩文件，使用与内联区域相同的 Tab 分隔请求格式。 |
| `output` 输出 FASTA | 否 | 空 | 留空时写入运行目录；自定义路径应位于工作区，并按格式填写扩展名。 留空时保存到本次运行目录；.gz 后缀启用 gzip 输出。 |
| `overwrite` 覆盖输出 | 否 | false | 默认 `false`；只有确认目标文件可替换时才设为 `true`。 允许替换已有的自定义输出文件。 |

## 结果怎么读

- 输出格式：`FASTA`、`FASTA.gz`、`TSV diagnostics`
- 输出包含按 ID/坐标/方向提取的序列及诊断；负链坐标和 1-based inclusive 边界必须逐条确认。
- 结果面板是快速检查入口；完整数据、运行清单和日志位于本次运行目录。

## 最小运行示例

```powershell
opentools run fasta.subsequence --param input=C:\data\input.fasta
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
