# Performance

OpenTools is local-first and Java-native. This page records a reproducible smoke
benchmark and the code-level optimizations behind it; it is not a claim of
universal speed.

## Benchmark environment

- Windows x64, JDK 21, OpenTools 0.12.0-SNAPSHOT.
- Each command includes JVM startup, project open, plugin scan, task execution, and
  result persistence.
- Measure the same command on your machine and compare medians of three runs.

## Datasets

| Dataset | Shape | Size |
| --- | --- | --- |
| `large.fa` | 100,000 records × 500 bp | 51.8 MB |
| `table-small.tsv` | 80,000 rows × 6 columns | 2.8 MB |
| `table-large.tsv` | 300,000 rows × 6 columns | 11.1 MB |

`table-small.tsv` stays on the in-memory transpose path; `table-large.tsv`
crosses the 8 MB AUTO threshold and uses the disk spool.

## Commands

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

## Current results

| Run | Wall time | Output check |
| --- | --- | --- |
| `fasta.stats`, 4 threads | 1.31 s | 100,000 sequences, 50,000,000 bases, GC 50%, N50 500 |
| `table.transpose`, in-memory | 0.89 s | 6 output rows, 0 spilled cells |
| `table.transpose`, disk | 3.85 s | 6 output rows, 1,800,000 spilled cells |

Times are single observations on a warm filesystem; CLI startup is a meaningful
part of the small-table result. Compare medians before drawing conclusions.

## Optimizations

- FASTA base counting uses one ASCII uppercase step and a single switch per base
  instead of `Character.toUpperCase` plus a second canonical-base test.
- In-memory transpose writes each output field directly and avoids allocating a
  temporary `ArrayList` for every transposed row.
- The disk path keeps the bounded SQLite spool for arbitrary-size inputs; the
  AUTO threshold avoids loading large tables into the heap.

## How to interpret

- Throughput depends on storage, CPU, JVM warmup, antivirus, and project size.
- The GUI adds rendering time; use the CLI numbers for pipeline throughput.
- If a workload is slower than expected, compare `transposeMode`, `threads`, and
  the run log before changing parameters.
