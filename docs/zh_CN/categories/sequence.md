# 序列处理

序列工具适合 FASTA/FASTQ 统计、筛选、转换、子序列提取、ORF 预测、SSR 挖掘、
引物检查（e-PCR）、MEME/MAST 基序分析、Sanger AB1 组装和 CDS 翻译。大多数记录型工具采用流式读取，
输入可为 gzip。

## 注意事项

- FASTA 子序列使用 1-based inclusive 坐标；
- 单条超长 FASTA 记录仍会在解析和变换时驻留内存；
- 自动序列类型按字符集推断，模棱两可时应显式选择；
- FASTQ AUTO 无法区分 Phred+64 与 Solexa+64；
- `.gz` 输出后缀需要与工具支持的压缩写入一致。

在应用内 Docs 中展开“序列处理”可打开全部 18 个内置工具。
