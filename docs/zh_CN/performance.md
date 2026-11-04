# 性能

OpenTools 是本地优先的 Java 原生工具。本页记录可复现的冒烟基准和对应的代码优化，
不代表所有机器上的绝对速度。

## 基准环境

- Windows x64、JDK 21、OpenTools 0.10.2-SNAPSHOT。
- 每条命令包含 JVM 启动、项目打开、插件扫描、任务执行和结果持久化。
- 请在本机对同一命令跑三次取中位数后比较。

## 数据集

| 数据集 | 规模 | 大小 |
| --- | --- | --- |
| `large.fa` | 100,000 条 × 500 bp | 51.8 MB |
| `table-small.tsv` | 80,000 行 × 6 列 | 2.8 MB |
| `table-large.tsv` | 300,000 行 × 6 列 | 11.1 MB |

`table-small.tsv` 走内存转置；`table-large.tsv` 超过 8 MB AUTO 阈值，走磁盘
暂存。

## 命令

```powershell
$jar = "C:\path\to\opentools.jar"
$project = "C:\work\project"
$bench = "C:\work\bench"

Measure-Command {
  cmd /c "java -jar $jar --project $project run fasta.stats --param input=$bench\large.fa --param threads=4 --json > $bench\fasta.json"
}
Measure-Command {
  cmd /c "java -jar $jar --project $project run table.transpose --param input=$bench\table-small.tsv --param output=$bench\small.tsv --param overwrite=true --json > $bench\small.json"
}
Measure-Command {
  cmd /c "java -jar $jar --project $project run table.transpose --param input=$bench\table-large.tsv --param output=$bench\large.tsv --param overwrite=true --json > $bench\large.json"
}
```

## 当前结果

| 运行 | 墙钟时间 | 输出核对 |
| --- | --- | --- |
| `fasta.stats`，4 线程 | 1.31 s | 100,000 条序列、50,000,000 碱基、GC 50%、N50 500 |
| `table.transpose`，内存 | 0.89 s | 6 行输出、0 个 spilled cell |
| `table.transpose`，磁盘 | 3.85 s | 6 行输出、1,800,000 个 spilled cell |

时间是热文件系统上的单次观察；小表的 CLI 启动占比很高。下结论前请比较中位数。

## 优化

- FASTA 碱基统计改为一次 ASCII 大写化和单个 switch，去掉
  `Character.toUpperCase` 加第二次 canonical 判断。
- 内存转置直接写每个字段，避免为每个转置行分配临时 `ArrayList`。
- 磁盘路径继续使用有界 SQLite 暂存；AUTO 阈值避免把大表读入堆。

## 结果解读

- 吞吐受磁盘、CPU、JVM 预热、杀毒软件和项目大小影响。
- GUI 还有渲染开销；流水线吞吐请以 CLI 数字为准。
- 如果比预期慢，先比较 `transposeMode`、`threads` 和运行日志，再改参数。
