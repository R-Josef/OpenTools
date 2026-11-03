# SSR 挖掘

`sequence.ssr` · 序列处理 · v1.0.0

## 一句话说明

在核酸 FASTA 中查找完美型简单重复序列（SSR，微卫星）。

扫描器在每个位置尝试 1 到 `maxMotifLength` 的 motif 长度，保留达到 `minRepeats` 的最短 motif，并输出极大完美重复。结果是 TSV，包含序列 ID、1-based 坐标、motif、重复次数和重复长度。

## 什么时候使用

- 从基因组、染色体或转录组序列中挖掘微卫星候选。
- 在引物设计或标记开发前，按 motif 长度统计重复。
- 检查候选序列中是否存在目标重复 motif。

## 输入准备

- 声明格式：`FASTA`
- 标准 A/C/G/T 核酸序列；小写会被接受并转成大写。
- N 等简并碱基不会作为 motif，但不会中断扫描。

### 输入示意

```text
>chr1
ACACACACGTTATATATATC
```

## 推荐运行步骤

1. 检查序列 ID，去掉不需要的载体或低质量记录。
2. 先用 `maxMotifLength=6`、`minRepeats=3` 和默认 `maxLoci`。
3. 查看位点表和按 motif 长度的计数。
4. 如果结果要用于引物设计或发表，保留 FASTA、参数和输出清单。
5. 大基因组中只有在能检查输出和磁盘占用时才提高 `maxLoci`。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `input` 输入 FASTA | 是 | 空 | 核酸 FASTA，可选用 gzip 压缩。 |
| `maxMotifLength` 最大 motif 长度 | 否 | 6 | 检查 1 到该值的所有 motif 长度（最大 10）。 |
| `minRepeats` 最小重复次数 | 否 | 3 | 连续 motif 拷贝的最小次数；设为 2 会增加输出。 |
| `maxLoci` 最大位点数 | 否 | 100000 | 达到该位点数后停止，并标记结果截断。 |
| `output` 输出 TSV | 否 | 空 | 留空时保存到运行目录。 |
| `overwrite` 覆盖输出 | 否 | false | 允许替换已有输出。 |

## 结果怎么读

- 输出格式：`TSV`，列为 `sequence`、`start`、`end`、`motif`、`repeats`、`length`、`motif_length`。
- `start` 与 `end` 为 1-based 闭区间坐标。
- `metrics.loci` 是位点数；`metrics.repeatedBases` 是重复区段总长度。
- `metrics.truncated=true` 且末尾出现 `# truncated at N loci`，表示扫描在 `maxLoci` 处停止。
- 只有表头表示没有完美重复达到阈值，这是有效结果，不是失败。

## 最小运行示例

```powershell
opentools run sequence.ssr --param input=C:\data\genome.fa --param maxMotifLength=6 --param minRepeats=3
```

把占位路径替换为工作区中的真实文件。参数名保持原样。

## 检查清单与失败边界

- 只报告完美重复；中断型或复合型重复需要其他工具。
- 同一位置取最短 motif，因此 `ATATAT` 会报告为 `AT`，不是 `ATATAT`。
- 简并碱基不会作为 motif；全 N 序列不会产生位点。
- 扫描复杂度约为序列长度乘 motif 长度；大基因组可能耗时，`maxLoci` 只限制输出，不限制运行时间。
- 默认保护已有输出。

## 相关下一步

- [序列 ORF 查找](tool:sequence.orf)
- [FASTA 统计](tool:fasta.stats)
- [FASTA 过滤](tool:fasta.filter)

[返回文档首页](opentools-doc:README.md)
