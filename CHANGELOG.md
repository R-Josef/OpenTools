# Changelog

## 0.13.1-SNAPSHOT

### Added

- The Workflows page now supports visual DAG editing: select and drag nodes,
  add/edit/delete steps, and edit step IDs, tools, dependencies, and parameters
  with two-way JSON synchronization.
- Ordinary tool forms add a **View job** button that selects the latest job for
  that tool on the Jobs page, or reports when no job exists.
- The Jobs page adds **View result** next to Refresh, opening the selected job
  on the Results page.
- Workflow-level actions and step-editing actions use separate toolbar groups;
  step ID, tool ID, and dependency fields stay single-line in the step dialog;
  the Projects recent-project selector keeps a single-line height.
- `workflow watch` and `workflow schedule` provide opt-in directory
  triggers and fixed-interval runs; watched files substitute `${watch.file}`
  and `${watch.name}` in the workflow JSON.
- Parameter-validation messages for required files, paths, ranges, integers, and
  decimals localize through `ValidationText` in the GUI form and CLI.
- `meme.run` no longer passes the unsupported `-p` flag to MEME builds without
  parallel support. MEME/MAST are included in the Settings external-tool check.
- Add a pinned MEME 5.5.9 Cygwin build script and regex patch, a Windows
  GitHub Actions build workflow, and `tools/meme` bundling for Windows
  releases. Relocated builds use `MEME_DATA_DIR`; MAST falls back to
  `-hit_list` when the Cygwin XML writer fails.
- Fix the Workflows view bottom toolbar being hidden by the run log.
- FASTA base counting removes `Character.toUpperCase` and a second canonical
  test per base; in-memory transpose writes fields directly instead of allocating
  a temporary row list. Add a reproducible performance benchmark page.
- Job error codes now resolve through `ErrorText` to English/Chinese messages in
  the Jobs table, Results panel, and CLI job failures; unknown codes keep the
  original detail.
- The GUI adds a Workflows view with a JSON editor, live DAG preview,
  validation, saved-project workflows, sequential execution, and per-step status
  colors.
- The Results panel parses result tables off the EDT and loads the visualization
  only when its tab is selected.
- `plot.synteny` draws multi-genome chord/ribbon links from a cross-genome
  pair table and a combined GFF3/GTF annotation, with block colors and a link cap.
- `plot.circos` now renders a real circular ideogram: one arc per sequence,
  proportional spans, label-colored feature ticks, sequence labels, and center
  counts.
- `plot.efp` now renders tissue-grouped expression maps with a sample map,
  tissue color bands, tissue-ordered samples, and a reordered processed matrix.
- Cross-process job cancellation: `jobs cancel` and MCP `opentools_cancel_job`
  mark active jobs in the shared project database; the owning process polls the
  request and records `CANCELLED`.
- `meme.run` and `mast.run`: MEME motif discovery and MAST scanning through
  installed MEME Suite executables, with XML capture, secure parsing, motif and
  hit TSV summaries, and external-tool diagnostics.
- `sequence.primer-check`:  electronic PCR against a genome FASTA with a
  tab-separated primer table, IUPAC bases, configurable mismatches and product
  window, nearest-downstream amplicon reporting, and truncation caps.
- `sequence.ssr`: perfect SSR mining from FASTA with configurable motif length,
  minimum repeats, and locus cap; reports 1-based coordinates and per-motif counts.
- `fastq.split`: split FASTQ by record count while preserving headers, sequences,
  and qualities.
- `sanger.assemble`: read AB1/FASTQ/FASTA Sanger reads, trim low-quality ends,
  assemble overlapping reads, optionally anchor them to a reference with
  indel-aware local alignment, call IUPAC heterozygous bases, and emit a full
  contig, reference-anchored consensus, an expanded consensus that applies
  supported insertions/deletions, variant/indel tables, and provenance BED that
  distinguishes read-supported, heterozygous, deleted, low-confidence, and
  reference-filled positions.
- The Results panel adds the Visualization tab only when a result has a
  visualization artifact; tools without visualization output no longer show a
  disabled placeholder tab.
- Fix Visualization image loading after a Sanger job by tracking the plot tab by
  component instead of a cached tab index.
- The Results panel adds an AB1 tab for `sanger.assemble` jobs, showing trace
  channels, basecalls, quality shading, per-read selection, horizontal zoom/fit,
  and vertical Y zoom/fit controls. The default Y range fits the highest peak,
  and signed trace values are interpreted correctly.
- `sanger.assemble` trims common sequencing primers and optional user-supplied
  primer/vector FASTA sequences from both read ends, records the evidence in
  `trimming.tsv`, and can use the reference alignment to write a vector-trimmed
  `insert.fa`.
- `sanger.assemble` writes `assembly-quality.tsv` and `warnings.tsv` with
  contig coverage, low-confidence bases, multiple-contig, repeat-ambiguity,
  reference-fill, and unresolved-vector diagnostics.
- `bed.blocks`: parse BED12 blockCount/blockSizes/blockStarts, validate block
  coordinates, and export block and intron intervals.
- `gxf.edit`: edit GFF3/GTF source, type, strand, and attributes while preserving
  annotation headers and gzip input.
- `table.pivot` adds `LIST` and `COUNT_DISTINCT` aggregations for text and
  multi-value cells, with a configurable list separator.
- `expression.differential`: two-group Welch t-test, Mann-Whitney U, or
  multi-group one-way ANOVA with log2 transformation, BH correction, fold
  change, and adjusted p-values.
- `go.gsea`: preranked GSEA with classic, weighted, or weighted p=2
  running-sum weights, duplicate-gene collapsing, deterministic
  gene-score permutations, NES, and adjusted p-values.
- `go.enrich` can propagate GO ancestors before enrichment and prune redundant
  terms by hit-set Jaccard overlap; `kegg.enrich` supports the same pruning.
- `tree.newick.metadata`: export branch lengths, support labels, depth, and
  descendant-leaf counts for every Newick node.
- `alignment.blast.alignments`: extract HSP coordinates, E-values, bit scores,
  qseq/hseq, and midlines from BLAST XML for pairwise alignment review.
- `annotation.meme.results`: convert MEME motif and MAST hit XML into readable
  motif and hit tables.
- Local BLAST now produces BLAST XML as the primary output and derives tabular
  and alignment TSVs from the same run.
- The Results panel adds a BLAST tab for local BLAST and
  `alignment.blast.alignments`: a query-coordinate overview, clickable hit
  selection, and a colored pairwise alignment view.
- The Results panel adds a lightweight MEME tab for `meme.run`: motif list,
  consensus, contributing-site table, and a SeqLogo-style motif logo rendered
  from MEME XML.
- `plot.collinearity` now plots MCScanX pairs as a gene-order dot plot between
  two GFF3/GTF-annotated genomes, with block coloring, unmapped-pair counts, and
  genome/chromosome metrics.
- Dependency-ordered JSON workflows run through `opentools workflow run` and
  MCP `opentools_run_workflow`: cycle detection, `${step.output}`,
  `${step.output[N]}`, `${step.metric.KEY}`, and `${step.runId}` placeholders,
  per-step artifacts and metrics, and stop/continue failure policies.
- `resource.manage`: versioned local GO/KEGG resource packs with immutable
  import, SHA-256 and entry counts, active-pack switching, and rollback by
  reactivating a retained pack. `go.enrich`, `go.level`, and `kegg.enrich`
  accept `resourceId` and record the resource id, version, and digest.
- `alignment.blast.remote`: NCBI BLAST URL API search with explicit upload
  consent, RID-based polling, cancellation-aware waits, tabular output, and
  RID/program/database/hit metrics. HTTPS is enforced except for loopback tests.
- `plot.dotplot` now also accepts BLAST outfmt 6 input and renders HSP segments
  on query/subject genome coordinates, grouped by percent identity, with
  optional qlen/slen columns and an HSP cap. The generic plot model carries
  optional axis maxima for these segment plots.
- Linux x64 and macOS x64/arm64 portable release packaging.
- GitHub Actions release matrix that builds and validates all platform
  artifacts before publishing a tagged GitHub Release.
- Plugin API 2.1 with plugin-scoped, namespace-isolated UTF-8 localization,
  live language switching, `MessageFormat` arguments, localized descriptors,
  page titles, tools, parameters, documentation, interactive pages, and result
  summaries.
- A three-argument `registerPage` convenience overload derives
  `page.<id>.title` automatically while retaining the custom-key form.
- Convention-based tool guides from host docs and plugin JARs, displayed before
  the generated descriptor reference with automatic language fallback.
- A unified Plugin API 2.1 resource contract keeps dot-separated keys while
  replacing the `param` node with `parameter`, fixes plugin metadata to
  `plugin.name/description`, derives result keys from the Tool ID, and locates
  tool guides at `docs/<language>/tool/<Tool ID>.md`.
- Detailed English and Chinese beginner guides now cover all 93 built-in tools,
  including input sketches, parameter selection, result interpretation,
  failure boundaries, and related next steps.
- Bundled default example plugin with the interactive Coin Runner tool, JSON
  run data, PNG result artwork, English/Chinese JAR resources, and
  default-enabled release packaging.

### Fixed

- Jobs now have a bounded concurrency gate, a real QUEUED state, persisted
  progress, cooperative cancellation with a terminal CANCELLED record, and
  startup recovery. Stale RUNNING/QUEUED jobs become FAILED with
  `JOB_INTERRUPTED`; closing the app marks remaining jobs interrupted instead of
  leaving project switching permanently blocked.
- `--json` output is UTF-8 end to end, including the packaged launcher, and
  non-ASCII characters are escaped as `\uXXXX`. Chinese aliases and paths stay
  valid JSON for PowerShell `ConvertFrom-Json`, Python, and Node even when the
  Windows console uses a legacy code page.
- CLI application-home resolution now matches the GUI (JAR location first), so
  `lang/`, `docs/`, and `plugins/` are found when the JAR is started from
  another directory.
- Every CLI subcommand now supports `--help`; `jobs cancel` returns the input
  error code when a job cannot be cancelled instead of reporting CANCELLED.
- External `extraArgs` reject response files and options that would override
  inputs, outputs, databases, remote mode, or thread counts. This closes a
  workspace-policy bypass for BLAST, DIAMOND, MUSCLE, trimAl, and HMMER.
- GFF3 attribute lists are split before percent-decoding, so escaped commas
  (`%2C`) stay inside one value. `gxf.inspect` can skip malformed lines and
  reports their line numbers and count.
- Concatenated CDS features honour the GFF3 phase of a 5'-partial first segment.
- Generic plots draw X-axis ticks and a legend; box plots draw a visible median
  line; transparent PNG export keeps its alpha; PDF export falls back to ASCII
  when the loaded font has no glyph for a label, and prefers an installed CJK
  font.
- The plot viewer offers width, height, DPI, and transparent-background options
  before export, and file inputs can reuse artifacts from recent jobs.
- Spill and BLAST cache directories are probed by writing a real file instead of
  trusting `Files.isWritable`, which reports false on some Windows ACLs.

### Changed

- `run --no-wait` was removed. A one-shot CLI process cannot keep running a
  background job; use the GUI or MCP for asynchronous execution.
- New pull-request CI workflow runs `mvn verify` on every push to `main` and
  every pull request.

- Plugin localization now comes from
  `META-INF/opentools/i18n/<language>.properties` in the owning plugin JAR.
  Private example-plugin keys were removed from host `lang/` files.
- Every registered Tool ID is explicitly bound to its plugin localizer.
  Missing resources fall back to `ToolDescriptor` and `ToolParameter` text.
  Invalid, duplicate, or oversized resources produce structured diagnostics.
- Tool terminology is now consistent across the UI, CLI JSON, docs, and public
  execution contracts: `ToolExecutionContext`, `ToolResult`, and
  `ToolExecutionService`.
- Plugin API docs, the developer guide, release package notes, compile SDK,
  plugin directory guide, and host language-file guide describe Plugin API 2.1.
- The developer guide now includes the complete manifest, ID, localization-key,
  and resource-limit contract, plus a Maven layout and packaging walkthrough.
  It explicitly documents that arbitrary resource keys are not automatically
  prefixed and explains third-party library packaging rules.

## 0.8.0-SNAPSHOT

### Added

- Public GitHub-facing README, user guides, task reference, file-format,
  CLI/MCP, plugin, external-tool, release-package, FAQ, and error-code docs.
- English public homepage and a linked `README.zh-CN.md` Chinese edition.
- Complete locale-scoped user documentation under `docs/en_US` and
  `docs/zh_CN`, including category pages and localized screenshots.
- Offline in-app Markdown Docs page with searchable navigation, category task
  lists, descriptor-generated parameter tables, safe local links/images, and
  all 86 built-in task pages.
- Pinned Windows x64 third-party tool manifest with official URLs, SHA-256
  values, licenses, source URLs, build recipes, and offline cache reuse.
- Windows x64 portable app-image pipeline using JDK 21 `jpackage`, with separate
  GUI and console launchers, self-contained runtime, bundled docs, licenses,
  and seven external tools.
- New `opentools-analysis` module and `BuiltInAnalysis` registration entry.
- Application version is generated from the Maven project version and shown in
  the GUI title, status bar, and Settings; CLI and MCP use the same source.
- External executables are discovered only from packaged tools, explicit task
  settings, `OPENTOOLS_*` variables, or the inherited `PATH`; machine-specific
  fallback directories have been removed.
- `fasta.filter`: streaming ID, regular-expression, length, GC, and N-content
  filters with a per-record reason report.
- `fasta.transform`: forward/reverse/complement/reverse-complement transforms,
  uppercase/lowercase modes, sequence-type checks, and invalid-character cleanup.
- `fasta.subsequence`: 1-based inclusive whole-record and region extraction,
  negative-strand reverse complement, duplicate source-ID diagnostics, and
  missing/out-of-range diagnostics.
- Gzip output support through the shared `Compression` utility.
- F1 examples and completion notes.
- F2 annotation/BED tools: filtering, conversion, overlap detection,
  representative transcript selection, ID mapping, annotation merge/split,
  diagnostics and safe repairs, gene-density windows, BED statistics, and
  BED-to-GFF3 conversion.
- BED3/BED3+ reader and writer with 0-based half-open validation.
- F3 alignment and annotation tools: BLAST XML/RBH/FASTQ/region workflows,
  HMMER hmmscan, Pfam and eggNOG imports, MUSCLE, and trimAl.
- Streaming FASTQ parser and FASTQ-to-FASTA query preparation.
- F4 expression normalization, Pearson/Spearman correlation, PCA, and
  differential-analysis input preparation.
- Generic serializable scatter/line/bar/stacked-bar/box/violin plotting with
  PCA, correlation, volcano, box, violin, and bar tasks.
- F5 offline GO/KEGG enrichment, GO hierarchy levels, and enrichment bar/bubble
  plots with hypergeometric tests and Benjamini-Hochberg correction.
- F6 Newick summary/subset/render, IQ-TREE, MCScanX input/run/import,
  collinearity/dot plots, and approximate Ka/Ks calculation.
- F7 sequence logos, gene-location/domain/motif plots, circular overviews, and
  eFP-style expression heatmaps.
- F8 HTSJDK-backed SAM/BAM/CRAM statistics, subsetting, coverage, VCF
  statistics/filtering, and BED mean coverage.
- F9 table filter, column selection, join, transpose, deduplication, pivot, and
  regex replacement with gzip input support.
- Completed F1 with FASTA rename/conversion, six-frame ORF prediction, FASTQ
  statistics, and quality/length/N filtering with per-read reasons.
- F1 limitation pass: 24 unambiguous NCBI genetic-code tables in ORF and CDS
  translation, Phred+33/Phred+64/Solexa+64 quality decoding, gzip region-file
  input, and RFC 4180-style multiline TSV conversion.
- Shared quoted delimited reader/writer used by preview, sequence conversion,
  table operations, and generic plotting.
- Random-access index for uniformly wrapped uncompressed genome FASTA.
- Streaming first pass for representative-transcript selection and explicit
  record bounds for overlap and annotation SPLIT operations.
- Disk-spooled `gxf.overlap` mode using an indexed temporary SQLite feature
  table, with automatic fallback from the bounded in-memory sweep.
- Deterministic average-linkage row/column clustering for heatmaps.
- OBO 1.4 parsing and alternate-ID normalization for GO levels.
- SeqLogo PWM computation from equal-length FASTA with pseudocounts and
  optional reverse-complement counting.
- Secure StAX conversion for BLAST XML and `alignment.hmmer.hmmsearch`.
- Reusable BLAST databases keyed by subject hash, makeblastdb version, program,
  and database type, with cross-process locking, atomic publication, refresh,
  interruption recovery, and Windows space-path staging.
- Bounded BLAST database caches with byte and entry limits, LRU metadata,
  per-use leases, active-entry protection, interrupted-eviction recovery, and
  configurable `cacheMaxMegabytes` / `cacheMaxEntries`.
- BAM/CRAM index queries, BAI creation, and interval-event coverage without
  per-base arrays.
- Optional QNAME-based overlapping mate deduplication for BAM/BED coverage,
  preserving spliced match loci.
- TBI indexes for compressed VCF and CSI indexes for BCF filter output, with
  sequence-dictionary and sort-order validation.
- Streaming table filter/select/replace/deduplicate, bounded indexed joins with
  compound keys and INNER/LEFT/RIGHT/FULL modes, plus incremental pivot
  SUM/MEAN/COUNT/MIN/MAX aggregation.
- `table.transpose` disk spool mode using a temporary SQLite cell table, with
  automatic fallback when the in-memory cell bound is reached.
- Temporary SQLite capacity limits for `gxf.overlap` and `table.transpose`,
  with stable disk-full/I/O/memory errors and guaranteed working-file cleanup.
- Process-level SQLite spool benchmark for 100,000-row transpose and 100,000
  sparse annotation intervals, including sampled working-set and temporary-file
  peaks.
- Bounded ordered parallel record processing for FASTA/FASTQ/GXF/BED, indexed
  BAM/BED coverage, VCF statistics, Ka/Ks, expression correlation, and heatmap
  distance rows.
- Indexed `hts.bam.subset` region-level parallelism with independent
  `SamReader` workers, deterministic coordinate merging, duplicate-region
  suppression, and optional queryable BAI output.
- Ordered `hts.vcf.filter` decision blocks with a single dictionary-order
  VCF/BCF writer and single-threaded TBI/CSI creation.
- Bounded record-block parallelism for `table.filter`, `table.select-columns`,
  and `table.replace`, preserving quoted and multiline logical records.
- Paired publication for BAM/VCF outputs and index sidecars, including rollback
  of a newly published primary file when the sidecar cannot be published.
- Unified `threads` parameters with `0=AUTO`, `1=sequential`, and capped explicit
  worker counts; AUTO thresholds vary by task I/O profile.
- F8 indexed-versus-linear process-level benchmark script.
- Swing project workflow: create/open projects, recent-project persistence,
  invalid-project diagnostics, active-job switch protection, and full runtime
  session rebuild after switching.
- Tools catalog category filtering with visible group headers, task counts, and
  a workflow-oriented category order. Global search resets to all categories.
- Reproducible target-environment, display-mode, GUI launcher, and 1 GB HTS
  pressure scripts under `scripts/`.
- Real external-tool validation for BLAST+ 2.17.0, DIAMOND 2.2.8, HMMER 3.4,
  MUSCLE 5.3, trimAl 1.5, IQ-TREE 2.4.0, and MCScanX 1.0.0.
- Physical 4K UI validation at 3840x2160/60 Hz with 150% Windows DPI and app
  scaling checks at 1.0 and 1.5.
- 1.042 GiB BAM, 2.000 GiB VCF, 1.115 GiB BCF, and 1.094 GiB filtered VCF.gz
  pressure runs with sampled peak working-set measurements.
- Reference-dependent CRAM subset coverage, including missing-reference failure
  and queryable output BAI.

### Changed

- Docs now selects `docs/en_US` or `docs/zh_CN` from the active interface
  language, with explicit English fallback for languages without a bundled guide.
- Changing interface language in Settings now rebuilds the main window in the
  same process and takes effect immediately instead of requiring a restart.
- External tools are discovered from the portable package first, then explicit
  paths, environment variables, and PATH.
- Windows child processes remove the private Java runtime/app directories from
  PATH and retain the executable directory, preventing launcher DLL conflicts.
- Microsoft VC++ runtime support is pinned and recorded for bundled BLAST+.
- Project version advanced to `0.8.0-SNAPSHOT`.
- GUI, CLI, and MCP runtimes register the analysis tasks through the same
  `ToolRegistry` and generate their JSON Schema from the task descriptors.
- Built-in task count increased from 14 to 17.
- Built-in task count increased again to 27 after F2.
- Built-in task count increased to 36 after F3.
- Built-in task count increased to 46 after F4.
- Built-in task count increased to 51 after F5.
- Built-in task count increased to 61 after F6.
- Built-in task count increased to 67 after F7.
- Built-in task count increased to 73 after F8.
- Built-in task count increased to 80 after F9.
- Built-in task count increased to 85 after completing F1, then to 86 with
  `alignment.hmmer.hmmsearch`.
- Filter and transform outputs are committed from temporary files only after a
  successful complete read.
- Disk overlap now streams an index-ordered SQLite sweep with bounded active
  intervals instead of issuing one interval query per annotation record.
- Mate deduplication includes read group in the template identity, preventing
  same-name reads from different read groups from being merged.
- Parallel record paths preserve input order, bound in-flight batches, propagate
  worker failures, and publish output only after successful completion.
- BAM subset reuses indexed region queries without per-worker resorting; the
  final merge remains ordered and the compressed writer is single-threaded.
- VCF filter validates dictionary order while writing retained records instead
  of pre-reading the complete input for an index pass.
- Table AUTO remains sequential for filter, column selection, and replacement;
  explicit worker counts remain available for measured high-cost cases.
- The task catalog now groups related tools instead of interleaving all 86 tasks
  alphabetically across domains.
- hmmscan now stages the complete pressed database (`h3f/h3i/h3m/h3p`) into its
  temporary workspace and reports missing sidecars explicitly.
- MCScanX input preparation now writes standard sequence/gene-ID coordinates
  instead of chromosome/type placeholders, and handles tools without a version
  command without failing after successful output.

### Verification

- Docs tests cover Markdown blocks, escaping, unsafe schemes, absolute and
  traversal paths, both language roots, local links/images, language switching,
  category navigation, all 86 tasks, fallback, and packaged classpath resources.
- Windows portable ZIP verification checks required runtime, docs, license, and
  external-tool entries; clean-PATH console startup and all tool discovery were
  tested from the app image.
- Added coverage for 100,000-record gzip streaming, coordinate boundaries,
  negative strand, invalid characters, duplicate IDs, missing IDs, and empty
  extraction results.
- CLI and MCP integration tests exercise the same F1 task contracts.
- `mvn clean verify` passes 207 tests with 0 failures, errors, or skips.
- Indexed BAM benchmark: 200,000 100-bp records, `subset` 1.25x and `coverage`
  1.17x faster than the linear fallback in a process-level Java 21 smoke test.
- SQLite spool benchmark, Java 21.0.9, three process-level runs:
  100,000-row DISK transpose median 4,209.5 ms with 421.4 MiB maximum sampled
  working set and 59.1 MiB sampled temporary SQLite peak; 100,000 sparse GFF
  intervals median 1,356.3 ms with 291.5 MiB maximum sampled working set and
  6.6 MiB temporary SQLite peak.
- A 20,000-interval overlap smoke test fell from a 132.9 s median for per-record
  queries to 1.01 s with the streaming sweep.
- Parallel smoke test, Java 21.0.9: FASTQ stats improved from 216 ms to 162 ms,
  GXF inspection from 150 ms to 101 ms, and VCF stats from 338 ms to 286 ms.
  All parallel outputs were byte-identical to sequential output.
- Careful-parallel process benchmark, Java 21.0.9, three runs per configuration:
  16-region indexed BAM subset improved from 1,860.18 ms to 1,579.14 ms
  (1.18x, sampled peak working set 549.84 to 611.14 MiB); 300,000-variant VCF
  filter improved from 1,999.67 ms to 1,961.60 ms (1.02x, 476.11 to 490.91 MiB).
  All output SHA-256 values matched.
- The same benchmark kept AUTO sequential for sub-threshold inputs. At 7.9 MiB,
  table filter/select/replace AUTO resolved to one worker; explicit four-worker
  medians were 1.02x, 0.98x, and 0.97x versus sequential, respectively.
- A separate 2,000,000-row (157.47 MiB) Java 21 smoke run did not justify table
  AUTO parallelism: explicit four-worker ratios were 1.00x for filter, 0.96x for
  column selection, and 1.01x for replacement. Table AUTO therefore remains
  sequential at all sizes; explicit `threads=2-64` is the opt-in path.
- A 100,000-record sparse `gxf.overlap` DISK run remained sequential at a
  1,298.74 ms median with a sampled 279.63 MiB median peak working set.

### Known limitations

- A single FASTA record still resides in memory while parsed and transformed.
- Subsequence extraction performs two streaming passes and retains request
  metadata plus occurrence counts for requested IDs.
- AUTO sequence typing is alphabet-based and may need an explicit type for
  ambiguous biological sequences.
- Region-file input is parsed into the existing request model and is not a
  streaming request engine.
- Genome random access is indexed only for uncompressed, uniformly wrapped
  FASTA; gzip or irregular wrapping falls back to in-memory storage.
- NCBI tables 27/28 are excluded because stop/amino-acid interpretation can be
  context-dependent.
- FASTQ AUTO cannot distinguish Phred+64 from Solexa+64 and filter AUTO requires
  a full detection pass.
- Overlap AUTO/DISK needs temporary disk space and bounds dense active intervals
  with `maxActiveRecords`; annotation SPLIT still uses a bounded in-memory cache.
- IN_MEMORY table transpose remains fully resident and bounded; AUTO/DISK modes
  require temporary disk space and are bounded by `maxTempMegabytes`. Pivot
  custom expressions and multi-value columns remain out of scope.
- Coverage mate deduplication is opt-in and slower than the default event path;
  indexed coverage still queries each BED interval independently.
- BLAST cache maintenance is best-effort under lock contention or filesystem
  errors and may briefly exceed its configured limit until active leases end;
  multi-host shared-cache consistency remains out of scope.
- Indexed VCF/BCF output requires a sequence dictionary and dictionary-ordered
  records; ordinary uncompressed VCF output remains unindexed.
- Large VCF/BCF performance and working-set pressure have been tested locally;
  results are environment-specific.
- Recent GUI projects are a single-machine setting; concurrent app instances use
  last-writer-wins behavior.
- Parallel record tasks still use one sequential decompression reader and one
  ordered output writer; small files can be slower with explicit extra threads.
- Table transpose/global-state tables, `gxf.overlap`, and final Java2D rendering
  remain sequential by design. `gxf.overlap` has no text-region random access;
  partitioning, ordered pair merging, and global `maxPairs` bounds did not show
  enough measured benefit to justify AUTO parallelism.

## 0.7.0-SNAPSHOT

### Added

- Reproducible P7 performance smoke script for startup, 200k-record FASTA
  statistics, and bounded 200k-row table preview.
- Font-scale override through `-Dopentools.ui.scale` with bounded 75%-250%
  behavior.
- Accessible names/descriptions for the main window, navigation, task catalog,
  parameter controls, job/result tables, plugin table, and interactive plot.
- Explicit security, data-boundary, error-code, P7 acceptance, and benchmark
  documentation.

### Changed

- Project version advanced to `0.7.0-SNAPSHOT`.
- New plug-ins are disabled by default. Copying a JAR never executes it until
  an explicit `plugins enable` / GUI enable action.
- Plugin JARs now have file, entry, individual-entry, and unpacked-size limits
  and reject absolute or traversal entry names.
- MCP HTTP uses a bounded worker pool, enforces JSON Content-Type, security
  response headers, longer explicit tokens, and stricter Origin behavior.
- Batch automation is capped at 100 items and batch files at 10 MB.
- Removed unused Batik dependency management.

### Known limitations

- Enabled Java plug-ins remain trusted in-process code and are not a security
  sandbox.
- MCP HTTP remains loopback-only and has no TLS or token rotation.
- Physical 4K at 150% Windows DPI has been validated. A 100% DPI and mixed
  multi-monitor matrix remains a separate display configuration.
- The recorded benchmark is a single-machine smoke baseline, not a statistically
  stable microbenchmark.

## 0.6.0-SNAPSHOT

### Added

- Dedicated `opentools-mcp` automation module using the same task registry,
  validation, job store, plugins, and execution service as the GUI and CLI.
- Draft 2020-12 JSON Schema generation for every registered task parameter.
- MCP over stdio and authenticated loopback HTTP for task search, description,
  validation, submission, status/waits, cancellation, retry, results, recent
  jobs, format detection, and sequential batches.
- Random bearer-token generation, loopback binding, local Origin checks, and
  request-size limits for the HTTP transport.
- Workspace path policy for MCP and batch automation with relative-to-project
  path resolution and overwrite rejection.
- Stable CLI JSON envelope with `ok`, `apiVersion`, `data`, and `error`.
- Stable CLI exit codes for input, dependency, execution, cancellation, and
  internal failures.
- Sequential JSON batch execution with dry-run and continue-on-error controls.
- OpenTools Agent Skill with MCP and CLI operating guidance.

### Changed

- Project version advanced to `0.6.0-SNAPSHOT`.
- Automation now records normalized/default task parameters before submission.
- Job failures in `--json` mode return structured error details instead of
  stack traces.

### Known limitations

- Batch execution is sequential and does not yet resolve a workflow DAG or
  artifact references between jobs.
- MCP HTTP has no TLS; it is restricted to loopback and a per-process token.
- MCP result reads return the bounded persisted result document, not streaming
  output while a task is running.
- stdio transport relies on the parent process for access control.

## 0.5.0-SNAPSHOT

### Added

- Plugin API 1.0 with declared plugin dependencies and requested capabilities.
- Separate child-first class loader per plugin, with dependency JARs on the
  plugin's private class path.
- Topological dependency loading, missing-dependency and cycle diagnostics,
  API compatibility checks, capability enforcement, and failure rollback.
- Persistent enable/disable state in `plugins/plugin-state.json`.
- CLI commands `plugins inspect`, `plugins enable`, and `plugins disable`.
- Automatic plugin discovery before CLI task execution.
- Enable/disable controls and API/dependency columns in the Swing plugin panel.
- Buildable example plugin with a registered task, page, and private data
  directory.
- Self-contained plugin API with public task contracts and a compile SDK.

### Changed

- Project version advanced to `0.5.0-SNAPSHOT`.
- Omitted capabilities default to the tool/page/data behavior.
- Plugin tools are removed from the shared registry when plugins are disabled
  or fail to initialize.

### Known limitations

- Java plugins still run inside the host JVM and are not strongly sandboxed.
- Plugin dependency declarations reference plugin IDs, not version ranges.
- Plugin scanning reloads currently enabled plugins.

## 0.4.0-SNAPSHOT

### Added

- Dedicated `opentools-visualization` module with serializable PlotSpec,
  typed heatmap/Venn/gene data, and PlotModel lineage documents.
- Headless PNG, SVG, and PDF rendering through a shared plot scene model.
- `visual.heatmap` with streamed matrix parsing, bounded fixed-seed sampling,
  missing/invalid diagnostics, deterministic row and column ordering,
  row z-score normalization, configurable palettes, and processed-matrix export.
- `visual.venn` with exact set intersections and automatic Venn/UpSet display
  selection.
- `visual.gene-structure` for selected GFF3/GTF loci with transcript, exon,
  CDS, strand, and coordinate tracks.
- Interactive Swing Plot result tab with hover details, wheel zoom, drag pan,
  fit-to-window, and PNG/SVG/PDF export.
- External language keys and P4 example inputs.

### Changed

- Project version advanced to `0.4.0-SNAPSHOT`.
- Task execution now records declared defaults in jobs, manifests, retries,
  and CLI runs before task execution.
- Plot inputs, styles, ordering, normalization, dimensions, DPI, formats, and
  lineage are persisted with every visual job.

### Known limitations

- Heatmap ordering is deterministic mean/name sorting; hierarchical clustering
  is not implemented yet.
- Oversized heatmaps use fixed-seed sampling for display rather than loading
  every source cell into the Swing viewer.
- Gene-structure parsing currently targets the common GFF3/GTF grouping model.
- PDF Chinese text requires an embeddable system font; PNG and SVG preserve
  Unicode labels independently.

## 0.3.0-SNAPSHOT

### Added

- Dedicated external-tool module for executable discovery and version checks.
- Shell-free process runner with merged output capture, timeout, cancellation,
  process-tree termination, and full run-log integration.
- Local BLAST+ workflow with automatic program selection for common sequence
  type combinations.
- Temporary BLAST database construction through `makeblastdb`.
- BLAST tabular best-hit summaries and result-table previews.
- DIAMOND `makedb` and `blastp` workflow with best-hit summaries.
- External tool diagnostics in CLI `doctor` and Swing Settings.
- Support for project, input, and output paths containing spaces and Chinese
  characters, including the BLAST+ Windows path workaround.

### Changed

- Project version advanced to `0.3.0-SNAPSHOT`.
- External-tool versions and actual program names are recorded in manifests.

## 0.2.0-SNAPSHOT

### Added

- GFF3/GTF unified parsing for features and attributes.
- Annotation inspection with feature and sequence summaries.
- Genome/annotation sequence-name and coordinate compatibility checks.
- Feature sequence extraction with `FEATURE`, `UPSTREAM`, `DOWNSTREAM`, and
  `WITH_FLANKS` modes.
- Negative-strand segment concatenation and strand-aware promoter extraction.
- CDS-to-protein translation with the standard genetic code.
- Bounded preview of large TSV/CSV files without retaining all rows in memory.
- Job retry from GUI and CLI.
- Copy a job's equivalent CLI command from the Desktop UI.
- Export displayed result-table data as TSV or CSV.
- OpenTools version and complete input provenance in run manifests.

### Changed

- Project version advanced to `0.2.0-SNAPSHOT`.
- English and Simplified Chinese language files expanded for all P2 tasks.

## 0.1.0-SNAPSHOT

### Added

- Java 21 Maven multi-module foundation.
- Swing/FlatLaf desktop workbench.
- FASTA statistics, extraction, merge, and split.
- SQLite job history and reproducible manifests.
- CLI task discovery, validation, execution, logs, cancellation, and plugins.
- Plugin loading foundation.
- External English and Simplified Chinese language files.
- GPL-3.0-or-later licensing and third-party notices.
