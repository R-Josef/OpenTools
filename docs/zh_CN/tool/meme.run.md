# MEME 基序发现

`meme.run` · 序列处理 · v1.0.0

## 一句话说明

在 FASTA 上运行 MEME，保留 motif XML，并导入 motif 汇总表。

外部 MEME 程序负责发现 motif；OpenTools 负责校验命令、记录运行日志、把 `meme.xml` 复制到运行目录，并解析 motif ID、名称、宽度、贡献位点和 E-value。

## 什么时候使用

- 在启动子、peak 或蛋白家族 FASTA 中发现保守 motif。
- 生成可供 [MAST](tool:mast.run) 扫描的 motif XML。
- 把 motif 发现参数和输出保存在同一条可复现运行记录中。

## 输入准备

- 声明格式：`FASTA`
- DNA 或蛋白序列，ID 唯一。
- 需要安装 MEME Suite 并在 `PATH` 中，或设置 `OPENTOOLS_MEME`，或提供 `executablePath`。
- 本工具不随包附带 MEME，属于外部依赖。

### 输入示意

```text
>seq1
ACGTACGTACGTACGT
>seq2
ACGTACGTACGTACGT
```

## 推荐运行步骤

1. 检查序列字母表，去掉重复或过短记录。
2. 先用 `molecule=DNA`、10 个 motif、宽度 6–50、4 线程。
3. 查看 motif 汇总；空 XML 是有效的“未发现 motif”结果。
4. 把生成的 XML 交给 [MAST](tool:mast.run) 扫描。
5. 保留 XML、汇总 TSV、命令日志和参数。

## 参数怎么选

| 参数 | 必填 | 默认值 | 选择建议 |
| --- | --- | --- | --- |
| `input` 输入序列 | 是 | 空 | DNA 或蛋白 FASTA。 |
| `molecule` 分子类型 | 否 | DNA | DNA 或 PROTEIN，对应 `-dna`/`-protein`。 |
| `motifs` Motif 数量 | 否 | 10 | 最多发现的 motif 数。 |
| `minMotifWidth` 最小宽度 | 否 | 6 | motif 最小宽度。 |
| `maxMotifWidth` 最大宽度 | 否 | 50 | motif 最大宽度，必须不小于最小值。 |
| `threads` 线程数 | 否 | 4 | MEME 工作线程数。 |
| `timeoutMinutes` 超时分钟 | 否 | 60 | 命令最长运行时间。 |
| `executablePath` MEME 可执行文件 | 否 | 空 | 可选的显式路径。 |
| `output` 输出 XML | 否 | 空 | 留空时在运行目录保存 `meme.xml`。 |
| `extraArgs` 额外 MEME 参数 | 否 | 空 | 可解析引号；保留选项会被拒绝。 |
| `overwrite` 覆盖输出 | 否 | false | 允许替换已有输出。 |

## 结果怎么读

- 输出格式：`MEME XML` 与 `TSV`。
- TSV 列为 `motif`、`name`、`width`、`sites`、`evalue`。
- `metrics.motifs` 和 `metrics.sites` 汇总解析结果。
- 有效运行但没有 motif 时，TSV 只有表头且指标为 0；先看 MEME 日志再判断是否为失败。

## 最小运行示例

```powershell
opentools run meme.run --param input=C:\data\promoters.fa --param motifs=10 --param molecule=DNA
```

把占位路径替换为工作区中的真实文件。参数名保持原样。

## 检查清单与失败边界

- 必须安装 MEME；找不到可执行文件会以 `EXTERNAL_TOOL_MISSING` 失败。
- 命令使用参数数组而非 shell 拼接；`extraArgs` 支持引号，但不能覆盖保留选项。
- 蛋白模式需要蛋白 FASTA；字母表混用可能得到空结果或失败。
- XML 在解析前会先复制；格式错误以 `MEME_XML_INVALID` 失败。
- 默认保护已有输出。

## 相关下一步

- [MAST 基序扫描](tool:mast.run)
- [FASTA 统计](tool:fasta.stats)
- [MEME 基序发现](tool:meme.run)

[返回文档首页](opentools-doc:README.md)
