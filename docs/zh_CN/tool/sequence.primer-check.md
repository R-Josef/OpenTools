# 引物检查（e-PCR）

`sequence.primer-check` · 序列处理 · v1.0.0

## 一句话说明

在基因组上定位引物对，并报告预测的扩增产物。

正向引物在正链上搜索；反向引物取反向互补后也在正链上搜索，因此一对引物对应跨区域的扩增。每个正向匹配报告满足产物长度窗口的最近下游反向位点。

## 什么时候使用

- 检查一对引物是否预期能在基因组或组装上扩增。
- 在实验前估算产物长度和坐标。
- 批量筛查引物的多结合位点或非预期产物。

## 输入准备

- 声明格式：`FASTA`、`TSV`
- 基因组：核酸 FASTA，可选用 gzip 压缩。
- 引物表：制表符分隔，表头含 `name`、`forward`、`reverse` 三列。
- 引物序列支持 A/C/G/T 和 IUPAC 简并碱基（R/Y/S/W/K/M/B/D/H/V/N）。
- `reverse` 列填写引物原始序列；工具内部使用其反向互补序列搜索。

### 输入示意

```text
name	forward	reverse
p1	ACGTACGT	TGCATGCA
```

## 推荐运行步骤

1. 确认引物方向：`forward` 结合正链，`reverse` 结合负链。
2. 先用 `maxMismatches=0` 验证完全匹配，再按需放宽到 1–3。
3. 把产物长度窗口设为实验预期；默认 50–3000 bp。
4. 下单前检查每个预测产物及其错配数。
5. 保留基因组、引物表、参数和输出 TSV。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `genome` 基因组 FASTA | 是 | 空 | 核酸基因组或 contig；正反链都会搜索。 |
| `primers` 引物表 | 是 | 空 | 含 name、forward、reverse 列的 TSV；支持 IUPAC 碱基。 |
| `maxMismatches` 最大错配数 | 否 | 0 | 每条引物允许的错配数；0 表示完全匹配。 |
| `minProductSize` 最小产物长度 | 否 | 50 | 扩增产物最小长度（bp）。 |
| `maxProductSize` 最大产物长度 | 否 | 3000 | 扩增产物最大长度（bp）。 |
| `maxAmplicons` 最大扩增产物数 | 否 | 100000 | 达到该产物数后停止，并标记截断。 |
| `output` 输出 TSV | 否 | 空 | 留空时保存到运行目录。 |
| `overwrite` 覆盖输出 | 否 | false | 允许替换已有输出。 |

## 结果怎么读

- 输出格式：`TSV`，列为 `primer`、`sequence`、`forward_start`、`forward_end`、`reverse_start`、`reverse_end`、`product_size`、`forward_mismatches`、`reverse_mismatches`。
- 坐标为 1-based 闭区间；`product_size = reverse_end - forward_start + 1`。
- `metrics.amplicons` 是预测产物数；`metrics.truncated` 和末尾 `# truncated` 行表示触发上限。
- 只有表头表示没有引物通过匹配和产物长度过滤，这是有效的阴性结果。

## 最小运行示例

```powershell
opentools run sequence.primer-check --param genome=C:\data\genome.fa --param primers=C:\data\primers.tsv
```

把占位路径替换为工作区中的真实文件。参数名保持原样。

## 检查清单与失败边界

- 每个正向匹配只报告最近的下游反向位点，不枚举所有远端次级产物。
- `maxMismatches` 按每条引物计算；输出分别给出两侧观察到的错配数。
- 基因组中的简并碱基（如 N）按错配处理，除非引物对应位置也是 N。
- 大基因组和大量引物的耗时/内存约与基因组长度乘引物数成正比；匹配数和产物数上限只约束输出，不约束运行时间。
- 每次运行最多 1000 对引物；更大的设计请拆分。
- 默认保护已有输出。

## 相关下一步

- [SSR 挖掘](tool:sequence.ssr)
- [序列 ORF 查找](tool:sequence.orf)
- [FASTA 统计](tool:fasta.stats)

[返回文档首页](opentools-doc:README.md)
