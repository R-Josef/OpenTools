# 多物种共线性

`plot.synteny` · 可视化 · v1.1.0

## 一句话说明

用弦/带状图展示三个及以上基因组之间的共线性连接。

基因组排列在圆周上；每条基因组的弧长按参与连接表的基因数分配。每个跨基因组配对是一条穿过中心的曲线，按 block 着色。

## 什么时候使用

- 汇总三个及以上基因组的 MCScanX 或直系同源连接。
- 查看某个 block 或比较由哪些基因组对主导。
- 在详细的双物种共线性点图之前，先做紧凑的多物种图。

## 输入准备

- 声明格式：`TSV`、`CSV`、`GFF3`、`GTF`
- 配对表列：`genome_a`、`gene_a`、`genome_b`、`gene_b`；`block` 可选。
- 合并注释：一个 GFF3/GTF，包含全部基因组的 gene 特征；基因 ID 必须全局唯一。
- 基因组标签来自配对表，不来自注释。

### 输入示意

```text
block	genome_a	gene_a	genome_b	gene_b
1	A	gA1	B	gB1
1	A	gA2	B	gB2
2	A	gA3	C	gC3
3	B	gB1	C	gC1
```

## 推荐运行步骤

1. 确认合并注释中的基因 ID 唯一。
2. 先用 `maxLinks=5000`；图仍可读时才提高。
3. 检查基因组弧、连接密度和 block 颜色。
4. 需要细节时改用双物种[共线性图](tool:plot.collinearity)。
5. 保留配对表、注释和运行清单。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `input` 跨基因组配对 | 是 | 空 | 含两侧 genome/gene 列的 TSV。 |
| `annotation` 合并注释 | 是 | 空 | 含全部基因组唯一基因 ID 的 GFF3/GTF。 |
| `blockColumn` Block 列 | 否 | block | 可选的 block 标识，用于连接着色。 |
| `genomeAColumn` 基因组 A 列 | 否 | genome_a | 第一侧的基因组标签。 |
| `geneAColumn` 基因 A 列 | 否 | gene_a | 第一侧的基因 ID。 |
| `genomeBColumn` 基因组 B 列 | 否 | genome_b | 第二侧的基因组标签。 |
| `geneBColumn` 基因 B 列 | 否 | gene_b | 第二侧的基因 ID。 |
| `maxLinks` 最大连接数 | 否 | 5000 | 限制绘制连接；结果会记录截断。 |
| `title` 图标题 | 否 | Multi-species Synteny | 图表标题。 |
| `subtitle` 副标题 | 否 | 空 | 可选副标题。 |
| `valueLabel` 数值轴标签 | 否 | 空 | 可选的数值标签。 |
| `width` 图像宽度 | 否 | 1200 | 导出宽度（像素）。 |
| `height` 图像高度 | 否 | 800 | 导出高度（像素）。 |
| `dpi` PDF DPI | 否 | 144 | PDF 导出分辨率。 |
| `exportFormats` 导出格式 | 否 | PNG_SVG_PDF | 图形产物格式。 |
| `output` 输出前缀 | 否 | 空 | 留空时保存到运行目录。 |
| `overwrite` 覆盖输出 | 否 | false | 允许替换已有输出。 |

## 结果怎么读

- 输出格式：`PNG`、`SVG`、`PDF`、`JSON`。
- 外弧是基因组；内部曲线是连接，按 block 着色。
- `metrics.genomes`、`metrics.links`、`metrics.blocks`、`metrics.unmapped` 汇总图形。
- `metrics.truncated=true` 表示达到连接上限，图是部分结果。

## 最小运行示例

```powershell
opentools run plot.synteny --param input=C:\data\pairs.tsv --param annotation=C:\data\combined.gff3
```

把占位路径替换为工作区中的真实文件。参数名保持原样。

## 检查清单与失败边界

- 合并注释中的基因 ID 必须唯一；重复 ID 保留首个特征。
- 注释中缺失的基因计入 `unmapped`，不绘制连接。
- 基因组弧按基因顺序而非染色体长度；这是关系概览，不是物理图谱。
- 连接过多会变成密集弦图；降低 `maxLinks` 或先聚合 block。
- 默认保护已有输出。

## 相关下一步

- [共线性图](tool:plot.collinearity)
- [导入 MCScanX 共线性](tool:mcscan.import)
- [Circos 环状概览](tool:plot.circos)

[返回文档首页](opentools-doc:README.md)
