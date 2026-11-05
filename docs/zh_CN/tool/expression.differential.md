# 差异表达检验

`expression.differential` · 表达 · v1.0.0

## 一句话说明

使用 Welch t 检验或 Mann-Whitney U 比较两组样本，并给出 BH 校正 P 值。

它完成一个明确、可复现的分析步骤。运行记录会保存规范化参数、输入文件哈希和日志；正式发表前仍应检查原始数据、参数和生物学解释。

## 什么时候使用

- 对表达矩阵或功能注释做探索性统计，生成可继续绘图和人工审核的结果表。
- 需要对单一步骤保留参数、日志和输出，便于重复运行或排查问题时。

## 输入准备

- 声明格式：`Expression matrix`、`Sample groups`
- 必须提供：`input`, `groups`
- 确认输入格式、ID、表头和坐标约定与参数描述一致，并保留原始文件。

### 格式示意

**input**

```text
@HD	VN:1.6	SO:coordinate
@SQ	SN:chr1	LN:1000
read1	0	chr1	10	60	8M	*	0	0	ACGTACGT	IIIIIIII
```


**groups**

```text
@HD	VN:1.6	SO:coordinate
@SQ	SN:chr1	LN:1000
read1	0	chr1	10	60	8M	*	0	0	ACGTACGT	IIIIIIII
```


## 推荐运行步骤

1. 先确认文件格式、序列名/列名和坐标约定一致。
2. 使用默认值或下表中的推荐值运行校验，先排除路径、表头和类型错误。
3. 提交任务后同时检查摘要、结果表和输出文件；不要只看“成功”状态。
4. 如需复现，保留原始输入、参数、运行日志和输出哈希。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `input` 表达矩阵 | 是 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 第一列为基因，表头为样本。 |
| `groups` 样本分组 | 是 | 空 | 选择真实且完整的输入文件，不用示例占位路径替代。 两列 sample/group 文件，必须包含两个组。 |
| `test` 统计检验 | 否 | WELCH_T | 优先使用默认模式；只有输入类型或分析目标明确要求时才切换。 两组比较使用的检验方法。 |
| `delimiter` 分隔符 | 否 | AUTO | 优先使用默认模式；只有输入类型或分析目标明确要求时才切换。 矩阵分隔符。 |
| `log2` Log2 转换 | 否 | false | 从默认值开始；只有在理解上方说明后才改变。 检验前应用 log2(x+1)。 |
| `adjustment` P 值校正 | 否 | BH | 优先使用默认模式；只有输入类型或分析目标明确要求时才切换。 多重检验校正方法。 |
| `output` 输出 TSV | 否 | 空 | 留空时写入运行目录；自定义路径应位于工作区，并按格式填写扩展名。 留空时保存到本次运行目录。 |
| `overwrite` 覆盖输出 | 否 | false | 默认 `false`；只有确认目标文件可替换时才设为 `true`。 允许替换已有输出。 |

## 结果怎么读

- 输出格式：`TSV`
- 输出每个基因的组均值、log2 fold change、检验统计量、原始 P 值和校正 P 值；两组至少各需两个重复。
- 结果面板是快速检查入口；完整数据、运行清单和日志位于本次运行目录。

## 最小运行示例

```powershell
opentools run expression.differential --param input=C:\data\input.bam --param groups=C:\data\groups.bam
```

把占位路径替换为工作区中的真实文件。参数名必须保持原样，布尔值使用 `true` 或 `false`。

## 检查清单与失败边界

- 格式、列名、坐标或资源版本不匹配时，任务会在发布输出前失败。
- 已有输出默认不会被覆盖；只有明确设置输出路径并接受覆盖时，才检查 `overwrite`。

## 相关下一步

- [准备差异分析输入](tool:expression.differential-input)
- [表达 PCA](tool:expression.pca)
- [表达标准化](tool:expression.normalize)

[返回文档首页](opentools-doc:README.md)
