# SAM/BAM/CRAM 子集

`hts.bam.subset` · 高通量测序 · v1.2.0

## 一句话说明

按 1-based inclusive 区域提取比对，有 BAM/CRAM 索引时使用索引，否则线性扫描。

它完成一个明确、可复现的分析步骤。运行记录会保存规范化参数、输入文件哈希和日志；正式发表前仍应检查原始数据、参数和生物学解释。

## 什么时候使用

- 统计或筛选 SAM/BAM/CRAM、BED 与 VCF/BCF 数据，为质控和结果核查提供可追踪记录。
- 需要对单一步骤保留参数、日志和输出，便于重复运行或排查问题时。

## 输入准备

- 声明格式：`SAM`、`BAM`、`CRAM`、`Regions`
- 必须提供：`input`, `regions`
- CRAM 可能还需要参考 FASTA；有索引的 BAM/CRAM 更适合区域访问，VCF/BCF 应保持排序和有效头信息。

### 格式示意

**input**

```text
@HD	VN:1.6	SO:coordinate
@SQ	SN:chr1	LN:1000
read1	0	chr1	10	60	8M	*	0	0	ACGTACGT	IIIIIIII
```


**regions**

```text
chr1:10-100
chr2:250-400
```


## 推荐运行步骤

1. 先确认文件格式、序列名/列名和坐标约定一致。
2. 使用默认值或下表中的推荐值运行校验，先排除路径、表头和类型错误。
3. 提交任务后同时检查摘要、结果表和输出文件；不要只看“成功”状态。
4. 如需复现，保留原始输入、参数、运行日志和输出哈希。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `input` 输入比对 | 是 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 SAM、BAM 或 CRAM。 |
| `reference` 参考 FASTA | 否 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 CRAM 未内嵌参考时需要。 |
| `regions` 区域 | 是 | 空 | 可选项通常先留空；填写时保持 ID、列名或语法与输入一致。 每行一个 CONTIG:START-END 区域。 |
| `threads` 线程数 | 否 | 0 | 大文件保留 `0` 自动选择；需要固定顺序或小文件时可用 `1`。 0 对大型有索引比对文件自动选择；1 关闭并行区域。 |
| `output` 输出比对 | 否 | 空 | 留空时写入运行目录；自定义路径应位于工作区，并按格式填写扩展名。 .bam 输出 BAM，否则输出 SAM。 |
| `createIndex` 创建 BAM 索引 | 否 | true | 从默认值开始；只有在理解上方说明后才改变。 BAM 输出时写入 BAI 侧文件。 |
| `overwrite` 覆盖输出 | 否 | false | 默认 `false`；只有确认目标文件可替换时才设为 `true`。 允许替换已有输出。 |

## 结果怎么读

- 输出格式：`SAM`、`BAM`
- 输出为指定区域的 SAM/BAM；区域是 1-based inclusive，BAM 默认还会生成 BAI 索引。
- 结果面板是快速检查入口；完整数据、运行清单和日志位于本次运行目录。
- 线程数只影响执行路径和速度；改变线程不应改变科学结果。发现差异时应比较日志、输入和参数。

## 最小运行示例

```powershell
opentools run hts.bam.subset --param input=C:\data\input.bam --param regions=chr1:10-100
```

把占位路径替换为工作区中的真实文件。参数名必须保持原样，布尔值使用 `true` 或 `false`。

## 检查清单与失败边界

- 确认排序、索引、参考基因组版本和序列字典一致；未排序或版本不匹配会产生错误或误导性结果。
- 已有输出默认不会被覆盖；只有明确设置输出路径并接受覆盖时，才检查 `overwrite`。

## 相关下一步

- [BAM 覆盖度](tool:hts.bam.coverage)
- [VCF 统计](tool:hts.vcf.stats)
- [过滤 VCF](tool:hts.vcf.filter)

[返回文档首页](opentools-doc:README.md)
