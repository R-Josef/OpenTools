# 表达 eFP

`plot.efp` · 可视化 · v1.1.0

## 一句话说明

绘制带组织色带、可复现样本排序和基因表达热图的 eFP 图谱。

与通用热图不同，eFP 可以读取样本表，按组织排序样本，并在矩阵上方为每个组织绘制一条彩色色带。

## 什么时候使用

- 展示一组基因在组织或发育阶段的表达。
- 需要按组织分组、同时保持可发表热图时。
- 导出的矩阵顺序要与图完全一致时。

## 输入准备

- 声明格式：`TSV`、`CSV`
- 表达矩阵：基因为行、样本为表头。
- 可选样本表：制表符分隔的 `sample`、`tissue` 两列。
- 样本表中缺失的样本归为 `Unassigned`。

### 输入示意

```text
gene	root1	leaf1	root2	leaf2
gene1	12	18	15	20
gene2	40	36	44	31
```

```text
sample	tissue
root1	root
leaf1	leaf
root2	root
leaf2	leaf
```

## 推荐运行步骤

1. 确认矩阵样本名与样本表完全一致。
2. 原始值用 `normalization=NONE`；比较基因模式用 `ROW_ZSCORE`。
3. 检查组织色带、样本顺序和导出的处理后矩阵。
4. 保留矩阵、样本表和运行清单，便于复现。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `input` 表达矩阵 | 是 | 空 | 基因为行、组织/样本为表头。 |
| `sampleMap` 样本表 | 否 | 空 | 可选的 `sample`/`tissue` TSV；样本按组织分组排序。 |
| `normalization` 数值标准化 | 否 | NONE | `NONE` 保留原值；`ROW_ZSCORE` 按基因行标准化。 |
| `exportData` 导出处理后数据 | 否 | true | 在图旁写出重排后的矩阵。 |
| `title` 图标题 | 否 | Expression eFP | 图表标题。 |
| `subtitle` 副标题 | 否 | 空 | 可选副标题。 |
| `valueLabel` 数值轴标签 | 否 | 空 | 可选的数值轴标签。 |
| `width` 图像宽度 | 否 | 1200 | 导出宽度（像素）。 |
| `height` 图像高度 | 否 | 800 | 导出高度（像素）。 |
| `dpi` PDF DPI | 否 | 144 | PDF 导出分辨率。 |
| `exportFormats` 导出格式 | 否 | PNG_SVG_PDF | 图形产物格式。 |
| `output` 输出前缀 | 否 | 空 | 留空时保存到运行目录。 |
| `overwrite` 覆盖输出 | 否 | false | 允许替换已有输出。 |

## 结果怎么读

- 输出格式：`PNG`、`SVG`、`PDF`、`JSON`。
- 矩阵上方彩色色带代表组织；列按组织排序，再按原始顺序排列。
- `metrics.sampleGroups` 是组织数；`metrics.genes`、`metrics.samples` 描述显示矩阵。
- 处理后 TSV 保持图中的顺序和值，可用于核对图形。

## 最小运行示例

```powershell
opentools run plot.efp --param input=C:\data\expression.tsv --param sampleMap=C:\data\samples.tsv
```

把占位路径替换为工作区中的真实文件。参数名保持原样。

## 检查清单与失败边界

- 样本表必须有 `sample`、`tissue` 表头；重复样本行保留最后一个组织。
- 缺失或非数值单元格沿用热图默认规则（缺失用行均值，非法值按缺失处理）。
- 样本表名称与矩阵不一致时，这些样本归为 `Unassigned`。
- 默认保护已有输出；确认目标前缀后才开启 `overwrite`。

## 相关下一步

- [热图](tool:visual.heatmap)
- [表达 PCA](tool:plot.pca)
- [表达相关性](tool:expression.correlation)

[返回文档首页](opentools-doc:README.md)
