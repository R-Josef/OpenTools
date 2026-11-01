# OpenTools examples

These fixtures use synthetic data and are safe to run without research data or
machine-specific configuration.

| Directory | Contents |
| --- | --- |
| `sequence/` | FASTA, FASTQ, ORF, filtering, renaming, and conversion |
| `annotation/` | GFF3/GTF and BED workflows |
| `alignment/` | BLAST XML/table conversion and annotation imports |
| `expression/` | Expression normalization, PCA, correlation, and plots |
| `enrichment/` | Offline GO enrichment and plots |
| `comparative-genomics/` | Newick, MCScanX, collinearity, and Ka/Ks |
| `visualization/` | Sequence logos, gene structures, motifs, Venn, and eFP |
| `hts/` | SAM, BED coverage, and VCF workflows |
| `tables/` | Filtering, joins, pivots, and quoted delimited data |
| `batch/` | JSON batch execution |
| `external-tools/` | Optional workflows using separately installed tools |

Each directory README uses paths relative to that directory. External programs
are discovered from the portable package, explicit task paths, `OPENTOOLS_*`
variables, or `PATH`; no fixed development installation directory is assumed.
