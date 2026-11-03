# MAST 基序扫描

`mast.run` · 序列处理 · v1.0.0

## 一句话说明

用 MEME XML motif 文件在 FASTA 序列上运行 MAST，保留命中 XML，并导入 1-based 命中表。

外部 MAST 程序负责扫描；OpenTools 记录命令日志、复制 `mast.xml`，并解析序列 ID、motif、坐标、链、p-value 和匹配文本。

## 什么时候使用

- 用 [MEME](tool:meme.run) 发现的 motif 扫描基因组、启动子或蛋白集合。
- 比较 motif 在不同序列中的出现情况。
- 导出命中坐标，供后续重叠、注释或可视化使用。

## 输入准备

- 声明格式：`MEME XML`、`FASTA`
- 含 motif 定义的 `meme.xml`。
- 与 motif 字母表一致的 DNA 或蛋白 FASTA。
- 需要安装 MAST 并在 `PATH` 中，或设置 `OPENTOOLS_MAST`，或提供 `executablePath`。
- 本工具不随包附带 MAST，属于外部依赖。

### 输入示意

```text
motifs.xml  +  sequences.fa
```

## 推荐运行步骤

1. 确认 motif XML 与序列使用相同字母表。
2. 用默认参数运行 `mast.run`，查看命中数。
3. 检查命中表的坐标和 p-value；只有表头是有效的“无命中”结果。
4. 保留 XML、TSV、日志和参数，便于复现。
5. 把坐标用于重叠、注释或可视化任务。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `motifs` MEME motif XML | 是 | 空 | MEME 或兼容工具产出的 `meme.xml`。 |
| `sequences` 序列 | 是 | 空 | 待扫描的 DNA 或蛋白 FASTA。 |
| `timeoutMinutes` 超时分钟 | 否 | 60 | 命令最长运行时间。 |
| `executablePath` MAST 可执行文件 | 否 | 空 | 可选的显式路径。 |
| `output` 输出 XML | 否 | 空 | 留空时在运行目录保存 `mast.xml`。 |
| `extraArgs` 额外 MAST 参数 | 否 | 空 | 可解析引号；保留选项会被拒绝。 |
| `overwrite` 覆盖输出 | 否 | false | 允许替换已有输出。 |

## 结果怎么读

- 输出格式：`MAST XML` 与 `TSV`。
- TSV 列为 `sequence`、`motif`、`start`、`end`、`strand`、`pvalue`、`match`。
- 坐标为 1-based 闭区间；MAST XML 的 position 从 0 开始，导入时已转换。
- `metrics.hits` 是命中数；`metrics.sequences` 是至少有一个命中的序列数。
- 只有表头表示没有命中通过 MAST 阈值；先看 XML/日志再判断是否为失败。

## 最小运行示例

```powershell
opentools run mast.run --param motifs=C:\data\meme.xml --param sequences=C:\data\promoters.fa
```

把占位路径替换为工作区中的真实文件。参数名保持原样。

## 检查清单与失败边界

- 必须安装 MAST；找不到可执行文件会以 `EXTERNAL_TOOL_MISSING` 失败。
- motif 与序列字母表必须一致；DNA/蛋白混用可能失败或没有命中。
- 解析器同时支持 `motif` 与 `motiff` 属性，并用 `match` 长度计算结束坐标。
- XML 格式错误会以 `MEME_XML_INVALID` 失败。
- 默认保护已有输出。

## 相关下一步

- [MEME 基序发现](tool:meme.run)
- [FASTA 统计](tool:fasta.stats)
- [MAST 基序扫描](tool:mast.run)

[返回文档首页](opentools-doc:README.md)
