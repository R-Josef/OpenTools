# BED12 区块分析

`bed.blocks` · 基因组注释 · v1.0.0

## 一句话说明

解析 BED12 区块，输出 block 和 intron 区间，并校验区块坐标。

它完成一个明确、可复现的分析步骤。运行记录会保存规范化参数、输入文件哈希和日志；正式发表前仍应检查原始数据、参数和生物学解释。

## 什么时候使用

- 检查或改写基因组注释，并验证序列名、坐标和 feature 关系。
- 需要对单一步骤保留参数、日志和输出，便于重复运行或排查问题时。

## 输入准备

- 声明格式：`BED12`、`BED12.gz`
- 必须提供：`input`
- 确认序列名与基因组一致；GFF3/GTF 坐标按 1-based inclusive 理解，BED 按 0-based half-open 理解。

### 格式示意

**input**

```text
chr1	0	100	feature1	0	+
chr1	50	150	feature2	0	-
```


## 推荐运行步骤

1. 先确认文件格式、序列名/列名和坐标约定一致。
2. 使用默认值或下表中的推荐值运行校验，先排除路径、表头和类型错误。
3. 提交任务后同时检查摘要、结果表和输出文件；不要只看“成功”状态。
4. 如需复现，保留原始输入、参数、运行日志和输出哈希。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `input` 输入 BED12 | 是 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 包含 blockCount、blockSizes 和 blockStarts 列的 BED12。 |
| `output` 输出 TSV | 否 | 空 | 留空时写入运行目录；自定义路径应位于工作区，并按格式填写扩展名。 留空时保存到本次运行目录。 |
| `overwrite` 覆盖输出 | 否 | false | 默认 `false`；只有确认目标文件可替换时才设为 `true`。 允许替换已有输出。 |

## 结果怎么读

- 输出格式：`TSV`
- 输出逐 block 和 intron 的区间；检查 blockStarts 是否相对 chromStart、blockSizes 是否与 blockCount 一致。
- 结果面板是快速检查入口；完整数据、运行清单和日志位于本次运行目录。

## 最小运行示例

```powershell
opentools run bed.blocks --param input=C:\data\input.bed
```

把占位路径替换为工作区中的真实文件。参数名必须保持原样，布尔值使用 `true` 或 `false`。

## 检查清单与失败边界

- 未知序列、反向坐标、超出基因组长度和父子 feature 缺失都应在下游解释前检查。
- 已有输出默认不会被覆盖；只有明确设置输出路径并接受覆盖时，才检查 `overwrite`。

## 相关下一步

- [检查基因组与注释匹配](tool:gxf.genome.match)
- [基因密度滑窗](tool:gxf.gene-density)
- [过滤 GFF3/GTF](tool:gxf.filter)

[返回文档首页](opentools-doc:README.md)
