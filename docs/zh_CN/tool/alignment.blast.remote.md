# 远程 BLAST 搜索

`alignment.blast.remote` · 比对 · v1.0.0

## 一句话说明

把查询序列提交到 NCBI BLAST URL API，轮询完成后保存表格结果。

它完成一个明确、可复现的分析步骤。运行记录会保存规范化参数、输入文件哈希和日志；正式发表前仍应检查原始数据、参数和生物学解释。

## 什么时候使用

- 执行局部比对、多序列比对、系统发育建模或整理这些程序的输出。
- 需要对单一步骤保留参数、日志和输出，便于重复运行或排查问题时。

## 输入准备

- 声明格式：`FASTA`
- 必须提供：`query`, `program`
- 确认查询/主体序列类型和格式正确；外部程序工具应检查可执行文件、版本、线程和超时。

### 格式示意

**query**

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
| `query` 查询 FASTA | 是 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 核酸或蛋白查询序列；只有 allowUpload 为 true 时才会发送到 NCBI。 |
| `program` BLAST 程序 | 是 | blastn | 优先使用默认模式；只有输入类型或分析目标明确要求时才切换。 NCBI BLAST 程序名。 |
| `database` 数据库 | 否 | nt | 可选项通常先留空；填写时保持 ID、列名或语法与输入一致。 NCBI 数据库名，例如 nt、nr、refseq_protein 或 swissprot。 |
| `maxHits` 最大命中数 | 否 | 100 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 提交给 NCBI 的 HITLIST_SIZE。 |
| `allowUpload` 允许上传 | 否 | false | 从默认值开始；只有在理解上方说明后才改变。 明确确认查询序列会发送到 NCBI。 |
| `timeoutSeconds` 超时秒数 | 否 | 600 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 等待远程搜索完成的总时间。 |
| `pollSeconds` 轮询间隔 | 否 | 5 | 先使用默认值；调整阈值时记录理由，并与输入数据规模或统计目标对应。 两次状态检查之间的秒数。 |
| `baseUrl` 服务地址 | 否 | https://blast.ncbi.nlm.nih.gov/Blast.cgi | 可选项通常先留空；填写时保持 ID、列名或语法与输入一致。 NCBI BLAST URL API 端点；除回环测试外必须使用 HTTPS。 |
| `output` 输出表格 | 否 | 空 | 留空时写入运行目录；自定义路径应位于工作区，并按格式填写扩展名。 留空时保存到本次运行目录。 |
| `overwrite` 覆盖输出 | 否 | false | 默认 `false`；只有确认目标文件可替换时才设为 `true`。 允许替换已有的自定义输出文件。 |

## 结果怎么读

- 输出格式：`TSV`
- 结果取决于所选输入和参数；检查输出文件、摘要指标和诊断信息是否与实验设计一致。
- 结果面板是快速检查入口；完整数据、运行清单和日志位于本次运行目录。

## 最小运行示例

```powershell
opentools run alignment.blast.remote --param query=C:\data\query.fasta --param program=blastn
```

把占位路径替换为工作区中的真实文件。参数名必须保持原样，布尔值使用 `true` 或 `false`。

## 检查清单与失败边界

- 外部程序的版本和参数会改变命中、比对或树；发表时记录程序版本和完整命令。
- 已有输出默认不会被覆盖；只有明确设置输出路径并接受覆盖时，才检查 `overwrite`。

## 相关下一步

- [trimAl 比对修剪](tool:alignment.trim)
- [双向最佳 BLAST 比对](tool:alignment.blast.reciprocal)
- [BLAST 对齐查看数据](tool:alignment.blast.alignments)

[返回文档首页](opentools-doc:README.md)
