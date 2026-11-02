# 比对与系统发育

该类别包含 BLAST+、DIAMOND、BLAST XML、FASTQ/区域 BLAST、HMMER、MUSCLE、
trimAl、Newick、IQ-TREE、MCScanX 和 Ka/Ks。

## 注意事项

- BLAST/DIAMOND/HMMER/MUSCLE/trimAl/IQ-TREE/MCScanX 依赖外部工具；
- 自动 BLAST 程序取决于 query/subject 分子类型；
- E-value、最大命中数、线程和超时应按数据规模调整；
- HMM 数据库必须包含 hmmscan 所需 pressed sidecar；
- IQ-TREE 和大型比对可能运行很久，必须有可写运行目录和足够磁盘空间；
- MCScanX 输入需要一致的基因/序列坐标和可解析 homology 结果。

在应用内 Docs 中展开“比对与系统发育”可打开全部 17 个相关工具。
