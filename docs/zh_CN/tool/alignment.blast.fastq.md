# FASTQ BLAST

`alignment.blast.fastq` · 比对 · v1.2.0

## 一句话说明

将 FASTQ 查询临时转换为 FASTA，并使用统一的本地 BLAST 流程。

它完成一个明确、可复现的分析步骤。运行记录会保存规范化参数、输入文件哈希和日志；正式发表前仍应检查原始数据、参数和生物学解释。

## 什么时候使用

- 执行局部比对、多序列比对、系统发育建模或整理这些程序的输出。
- 需要对单一步骤保留参数、日志和输出，便于重复运行或排查问题时。

## 输入准备

- 声明格式：`FASTQ`、`FASTQ.gz`
- 必须提供：`query`, `subject`
- 确认查询/主体序列类型和格式正确；外部程序工具应检查可执行文件、版本、线程和超时。

### 格式示意

**query**

```text
@read1
ACGTACGT
+
IIIIIIII
```


**subject**

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
| `query` 查询 FASTQ | 是 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 FASTQ 或 FASTQ.gz 查询序列。 |
| `subject` 目标 FASTA | 是 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 目标序列。 |
| `program` BLAST 程序 | 否 | AUTO | 优先使用默认模式；只有输入类型或分析目标明确要求时才切换。 BLAST 程序或 AUTO。 |
| `evalue` E-value 阈值 | 否 | 1.0E-5 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 报告的最大 E-value。 |
| `maxTargetSeqs` 最大目标序列数 | 否 | 10 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 每个查询的最大命中数。 |
| `threads` 线程数 | 否 | 4 | 默认值已按外部程序配置；仅在 CPU、内存或调度策略明确允许时增加。 BLAST 工作线程数。 |
| `timeoutMinutes` 超时分钟数 | 否 | 30 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 外部命令最大运行时间。 |
| `blastBin` BLAST bin 目录 | 否 | 空 | 选择可写目录；批量输出前确认磁盘空间和命名冲突。 包含 BLAST+ 的目录。 |
| `cacheEnabled` 复用 BLAST 数据库 | 否 | true | 从默认值开始；只有在理解上方说明后才改变。 按 subject 哈希、makeblastdb 版本、程序和数据库类型复用数据库。 |
| `refreshDatabase` 刷新 BLAST 缓存 | 否 | false | 从默认值开始；只有在理解上方说明后才改变。 运行 BLAST 前重建匹配的缓存条目。 |
| `cacheDirectory` BLAST 缓存目录 | 否 | 空 | 选择可写目录；批量输出前确认磁盘空间和命名冲突。 可选现有可写目录；默认使用 OpenTools 系统临时缓存。 |
| `cacheMaxMegabytes` BLAST 缓存容量上限 | 否 | 10240 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 超过该容量（MiB）时按最久未用顺序淘汰数据库条目。 |
| `cacheMaxEntries` BLAST 缓存条目上限 | 否 | 128 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 超过该条目数时按最久未用顺序淘汰数据库条目。 |
| `output` 输出 BLAST 表格 | 否 | 空 | 留空时写入运行目录；自定义路径应位于工作区，并按格式填写扩展名。 留空时保存到本次运行目录。 |
| `extraArgs` 额外 BLAST 参数 | 否 | 空字符串 | 可选项通常先留空；填写时保持 ID、列名或语法与输入一致。 支持引号的额外 CLI 参数。 |
| `overwrite` 覆盖输出 | 否 | false | 默认 `false`；只有确认目标文件可替换时才设为 `true`。 允许替换已有输出。 |

## 结果怎么读

- 输出格式：`BLAST tabular`、`TSV`
- 输出为 BLAST 表格；先核对 query/subject ID、E-value、覆盖度和命中数量，再判断同源关系。
- 结果面板是快速检查入口；完整数据、运行清单和日志位于本次运行目录。
- 线程数只影响执行路径和速度；改变线程不应改变科学结果。发现差异时应比较日志、输入和参数。

## 最小运行示例

```powershell
opentools run alignment.blast.fastq --param query=C:\data\query.fastq --param subject=C:\data\subject.fasta
```

把占位路径替换为工作区中的真实文件。参数名必须保持原样，布尔值使用 `true` 或 `false`。

## 检查清单与失败边界

- 外部程序的版本和参数会改变命中、比对或树；发表时记录程序版本和完整命令。
- `extraArgs` 会原样传给外部程序；先核对引号、冲突选项和版本兼容性。
- 已有输出默认不会被覆盖；只有明确设置输出路径并接受覆盖时，才检查 `overwrite`。

## 相关下一步

- [trimAl 比对修剪](tool:alignment.trim)
- [DIAMOND 蛋白比对](tool:alignment.diamond)
- [双向最佳 BLAST 比对](tool:alignment.blast.reciprocal)

[返回文档首页](opentools-doc:README.md)
