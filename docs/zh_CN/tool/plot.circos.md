# Circos 环状概览

`plot.circos` · 可视化 · v1.1.0

## 一句话说明

绘制带特征环和标签的染色体环状 ideogram。

每条序列按该序列最大特征坐标分配弧长；特征是环内的彩色刻度，并按 label 分组。中心显示序列数和特征数。

## 什么时候使用

- 需要全基因组染色体和特征分布的快速概览。
- 按序列和特征类别展示基因、TE 或注释密度。
- 在进入完整 Circos 配置前，先产出可复现的环状图。

## 输入准备

- 声明格式：`TSV`、`CSV`
- 必需列：`seq_id`、`start`、`end`；`label` 可选。
- 坐标为 1-based、start ≤ end，单位为 bp。
- 每条序列的弧长按该序列最大 end 缩放；没有显式染色体长度时，由最大特征坐标决定。

### 输入示意

```text
seq_id	start	end	label
chr1	1	2000000	gene-rich
chr1	2100000	2400000	repeat-rich
chr2	1	1500000	gene-rich
```

## 推荐运行步骤

1. 确认序列 ID、坐标约定以及 start ≤ end。
2. 特征表每行一个区间；缺失 label 会归为 `feature`。
3. 用方形画布（例如 800×800），圆环更易读。
4. 发表前检查序列标签、环比例和特征颜色。
5. 保留表格、参数和运行清单，便于复现。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `input` 特征表 | 是 | 空 | 含 sequence/start/end 和可选 label 的 TSV/CSV。 |
| `sequenceColumn` 序列列 | 否 | seq_id | 序列标识列。 |
| `startColumn` 起始列 | 否 | start | 特征起始列（1-based）。 |
| `endColumn` 结束列 | 否 | end | 特征结束列（1-based，且 ≥ start）。 |
| `labelColumn` 标签列 | 否 | label | 特征类别列；缺失值归为 `feature`。 |
| `title` 图标题 | 否 | Circos Overview | 图表标题。 |
| `subtitle` 副标题 | 否 | 空 | 可选副标题。 |
| `valueLabel` 数值轴标签 | 否 | 空 | 可选的数值轴标签。 |
| `width` 图像宽度 | 否 | 1200 | 导出宽度（像素）；方形画布效果最好。 |
| `height` 图像高度 | 否 | 800 | 导出高度（像素）。 |
| `dpi` PDF DPI | 否 | 144 | PDF 导出分辨率。 |
| `exportFormats` 导出格式 | 否 | PNG_SVG_PDF | 图形产物格式。 |
| `output` 输出前缀 | 否 | 空 | 留空时保存到运行目录。 |
| `overwrite` 覆盖输出 | 否 | false | 允许替换已有输出。 |

## 结果怎么读

- 输出格式：`PNG`、`SVG`、`PDF`、`JSON`。
- 外环是一条序列；弧长按该序列最大坐标比例分配。
- 内环刻度是特征，按 label 着色；序列名在外环外侧。
- 中心显示序列数和特征数。
- 精确坐标以源表为准；图是概览，不是坐标读数器。

## 最小运行示例

```powershell
opentools run plot.circos --param input=C:\data\features.tsv --param width=800 --param height=800
```

把占位路径替换为工作区中的真实文件。参数名保持原样。

## 检查清单与失败边界

- 空表、非数值坐标或 start > end 会被拒绝或跳过；先检查输入。
- 没有显式染色体长度时，弧长由最大特征 end 决定，稀疏表可能放大短染色体。
- 特征过多会变成密集刻度；环不可读时先聚合区间。
- 标签过长会重叠；可缩短类别名或增大画布。
- 默认保护已有输出。

## 相关下一步

- [基因位置](tool:plot.gene-location)
- [点图](tool:plot.dotplot)
- [共线性图](tool:plot.collinearity)

[返回文档首页](opentools-doc:README.md)
