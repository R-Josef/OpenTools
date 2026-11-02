# 文件格式

## FASTA 与 FASTQ

FASTA 记录由 `>` 标题和序列行组成；FASTQ 使用四行记录。输入可选用 gzip。
FASTA 子序列和 GFF 区间使用 1-based inclusive 坐标。BED 使用 0-based half-open
坐标。工具不会静默把两种坐标语义混用。

FASTQ 质量编码支持 Phred+33、Phred+64 和 Solexa+64。AUTO 不能在所有数据中区分
Phred+64 与 Solexa+64，必要时显式选择。

## GFF3 与 GTF

GFF3/GTF 使用 1-based inclusive 坐标。序列名必须与参考 FASTA 一致。读取器会
报告列数、坐标、strand、phase 和属性结构问题。注释修复工具只执行有明确策略的
安全修复，不静默猜测缺失业务字段。

## BED

支持 BED3 和 BED3+。前三个字段是 `chrom`、`start`、`end`，其中
`0 <= start < end`。strand 为 `+` 或 `-`；其他扩展列按工具说明读取。

## 分隔文本

TSV/CSV 支持 RFC 4180 风格引号、双引号转义和跨行字段。第一行默认是表头。
`AUTO` delimiter 会检测 Tab、逗号和分号；歧义数据应显式选择分隔符。

## SAM、BAM、CRAM

SAM/BAM/CRAM 通过 HTSJDK 读取。CRAM 在参考未内嵌时需要 reference FASTA。
有索引时，区域子集和覆盖度会优先使用 BAI/CRAI；无索引时部分工具退回线性扫描。

## VCF 与 BCF

输入可为普通 VCF、bgzip VCF 或 BCF。索引输出要求输入有 `##contig` 序列字典且
记录按字典顺序排列。压缩 VCF 使用 TBI，BCF 使用 CSI；普通未压缩 VCF 不创建索引。

## Newick

支持常见 Newick 树、带长度/支持值的内部节点和标准引用标签。绘制与子树提取不会
修改源文件；异常括号、缺失分号或未知引用会给出诊断。

## 图像与表格产物

绘图工具可输出 PNG、SVG、PDF 和 JSON PlotModel/数据。PDF 中文字体取决于可用系统
字体；PNG 和 SVG 能独立保留 Unicode 标签。表格预览有界，完整导出以工具产物为准。
