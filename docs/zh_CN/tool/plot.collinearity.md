# 共线性图

`plot.collinearity` · 可视化 · v1.1.0

## 一句话说明

把 MCScanX 共线性配对画成两个注释基因组之间的基因顺序点图。

每个基因组按染色体和基因顺序展开，每个点代表一对共线基因；按配对数量给 block 着色，主要共线区块会自然突出。

## 什么时候使用

- 查看 MCScanX / `mcscan.import` 的结果，或作为发表前的共线性检查图。
- 判断某次共线性分析是否被单一 block 主导、是否有大量未匹配基因、是否缺少预期的染色体对。
- 在两个基因组注释和基因 ID 已经核对后做快速比较。

## 输入准备

- 声明格式：`TSV`、`CSV`
- 配对表需要 `block`、`gene_a`、`gene_b` 列（列名可配置）。
- `annotationA`、`annotationB` 为 GFF3 或 GTF，需含 `gene` 特征；若只有 `mRNA`/`transcript` 也会使用。
- 基因 ID 取自 GFF3 `ID`（去掉 `gene:` 前缀）或 GTF `gene_id`，必须与 `gene_a`/`gene_b` 一致。

### 输入示意

```text
block	block_score	gene_a	gene_b	pair_index
1	100	gA1	gB1	1
1	100	gA2	gB2	2
2	50	gA3	gB3	1
```

```text
##gff-version 3
chr1	test	gene	1	100	.	+	.	ID=gene:gA1
chr1	test	gene	200	300	.	+	.	ID=gene:gA2
```

## 推荐运行步骤

1. 用 `mcscan.import` 导入 MCScanX 结果并确认配对表。
2. 确认注释中包含配对表用到的全部基因 ID。
3. 用两个注释和配对表运行 `plot.collinearity`。
4. 查看 plotted/unmapped 计数；未匹配通常意味着缺少基因特征或 ID 不一致。
5. 保留配对表、两个注释和运行清单，便于复现。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `input` 共线性表 | 是 | 空 | `mcscan.import` 的输出；需要 block 和基因列。 |
| `annotationA` 基因组 A 注释 | 是 | 空 | 基因组 A 的 GFF3/GTF；ID 必须匹配 `gene_a`。 |
| `annotationB` 基因组 B 注释 | 是 | 空 | 基因组 B 的 GFF3/GTF；ID 必须匹配 `gene_b`。 |
| `blockColumn` Block 列 | 否 | block | 共线性 block 的列名。 |
| `geneAColumn` 基因组 A 基因列 | 否 | gene_a | 基因组 A 的基因 ID 列。 |
| `geneBColumn` 基因组 B 基因列 | 否 | gene_b | 基因组 B 的基因 ID 列。 |
| `maxBlocks` 着色 block 数 | 否 | 20 | 按配对数量取前 N 个 block 着色，其余归为 “Other blocks”。 |
| `title` 图标题 | 否 | Collinearity | 图表标题。 |
| `subtitle` 副标题 | 否 | 空 | 可选副标题。 |
| `valueLabel` 数值轴标签 | 否 | 空 | 可选的 Y 轴标签覆盖。 |
| `width` 图像宽度 | 否 | 1200 | 导出宽度（像素）。 |
| `height` 图像高度 | 否 | 800 | 导出高度（像素）。 |
| `dpi` PDF DPI | 否 | 144 | PDF 导出分辨率。 |
| `exportFormats` 导出格式 | 否 | PNG_SVG_PDF | 图形产物格式。 |
| `output` 输出前缀 | 否 | 空 | 留空时保存到运行目录。 |
| `overwrite` 覆盖输出 | 否 | false | 允许替换已有输出。 |

## 结果怎么读

- 输出格式：`PNG`、`SVG`、`PDF`、`JSON`。
- X/Y 是累加基因顺序坐标；染色体边界隐含在基因顺序中。
- `pairs` 是实际绘制的基因对数；`unmapped` 是任一注释中找不到 ID 的配对数。
- `genesA`/`genesB`、`chromosomesA`/`chromosomesB` 描述两个注释基因组。
- 密集对角线表示顺序保守；偏离对角线的簇可能提示重排或注释问题。

## 最小运行示例

```powershell
opentools run plot.collinearity --param input=C:\data\collinearity.tsv --param annotationA=C:\data\genomeA.gff3 --param annotationB=C:\data\genomeB.gff3
```

把占位路径替换为工作区中的真实文件。参数名保持原样。

## 检查清单与失败边界

- 配对为空，或所有基因都未匹配时，会以 `COLLINEARITY_EMPTY` 失败；先看 `unmapped` 计数。
- 注释中没有 gene/mRNA/transcript 特征时，会以 `COLLINEARITY_ANNOTATION_EMPTY` 失败。
- GFF3 的 `gene:` 前缀会被去掉；其他 ID 约定（如版本后缀）需要在运行前统一。
- 默认保护已有输出；确认目标前缀后才使用 `overwrite`。

## 相关下一步

- [导入 MCScanX 共线性](tool:mcscan.import)
- [点图](tool:plot.dotplot)
- [BLAST 点图](tool:plot.dotplot)

[返回文档首页](opentools-doc:README.md)
