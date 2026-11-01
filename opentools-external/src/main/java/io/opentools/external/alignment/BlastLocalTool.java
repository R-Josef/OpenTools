package io.opentools.external.alignment;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
import io.opentools.core.util.Hashing;
import io.opentools.external.ExternalRuntime;
import io.opentools.external.process.ExternalArguments;
import io.opentools.external.process.ExternalProcessException;
import io.opentools.external.process.ProcessResult;
import io.opentools.external.process.ProcessRunner;
import io.opentools.external.process.TemporaryFiles;
import io.opentools.external.sequence.SequenceKind;
import io.opentools.external.sequence.SequenceKindDetector;
import io.opentools.external.tool.ExecutableLocator;
import io.opentools.external.tool.ExternalToolSpecs;
import io.opentools.external.tool.ToolStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlastLocalTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "alignment.blast.local",
            "Local BLAST",
            "Build a BLAST database and compare query sequences with a local subject FASTA.",
            "Alignment",
            List.of("BLAST", "blastn", "blastp", "本地 BLAST", "序列比对"),
            List.of("FASTA", "FASTA.gz"),
            List.of("BLAST tabular", "TSV"),
            "1.2.0",
            List.of(
                    ToolParameter.requiredFile("query", "Query FASTA", "Query sequences."),
                    ToolParameter.requiredFile("subject", "Subject FASTA", "Subject sequences used to build a temporary BLAST database."),
                    new ToolParameter(
                            "program",
                            "BLAST program",
                            "AUTO selects a program from query and subject sequence alphabets.",
                            ParameterType.ENUM,
                            false,
                            "AUTO",
                            List.of("AUTO", "BLASTN", "BLASTP", "BLASTX", "TBLASTN", "TBLASTX"),
                            null,
                            null,
                            false,
                            null
                    ),
                    ToolParameter.decimal("evalue", "E-value threshold", "Maximum E-value for reported hits.", 1e-5, 0.0, 1e9),
                    ToolParameter.integer("maxTargetSeqs", "Maximum target sequences", "Maximum subject hits per query.", 10, 1, 10_000),
                    ToolParameter.integer("threads", "Threads", "Number of BLAST worker threads.", 4, 1, 256),
                    ToolParameter.integer("timeoutMinutes", "Timeout minutes", "Maximum runtime for each external command.", 30, 1, 1440),
                    ToolParameter.optionalOutputFile("output", "Output BLAST table", "Leave blank to save inside the run directory."),
                    ToolParameter.optionalDirectory("blastBin", "BLAST bin directory", "Directory containing BLAST+ executables."),
                    ToolParameter.bool("cacheEnabled", "Reuse BLAST database",
                            "Reuse an immutable database keyed by subject hash, BLAST version, program, and database type.",
                            true),
                    ToolParameter.bool("refreshDatabase", "Refresh BLAST cache",
                            "Rebuild the cache entry even when a matching database exists.", false),
                    ToolParameter.optionalDirectory("cacheDirectory", "BLAST cache directory",
                            "Optional cache directory. Defaults to the system temporary OpenTools cache."),
                    ToolParameter.integer("cacheMaxMegabytes", "BLAST cache limit",
                            "Least-recently-used cache entries are evicted above this size.",
                            BlastDatabaseCache.DEFAULT_MAX_MEGABYTES, 1, 1_048_576),
                    ToolParameter.integer("cacheMaxEntries", "BLAST cache entry limit",
                            "Least-recently-used cache entries are evicted above this count.",
                            BlastDatabaseCache.DEFAULT_MAX_ENTRIES, 1, 1_000_000),
                    new ToolParameter(
                            "extraArgs",
                            "Extra BLAST arguments",
                            "Optional quote-aware BLAST CLI arguments. They are passed without a shell.",
                            ParameterType.TEXT,
                            false,
                            "",
                            List.of(),
                            null,
                            null,
                            true,
                            null
                    ),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing custom output file.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult base = validateParameters(request);
        Path output = request.path("output");
        if (output != null && Files.exists(output) && !request.bool("overwrite")) {
            base = base.merge(ValidationResult.of(ValidationIssue.error(
                    "output", "Output file already exists: " + output, "Choose another path or enable overwrite."
            )));
        }
        try {
            ExternalArguments.parse(request.string("extraArgs"));
        } catch (IllegalArgumentException ex) {
            base = base.merge(ValidationResult.of(ValidationIssue.error(
                    "extraArgs", ex.getMessage(), "Close the quoted argument or remove it."
            )));
        }
        return base;
    }

    @Override
    public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        Path query = request.path("query");
        Path subject = request.path("subject");
        Path output = request.path("output");
        if (output == null || output.toString().isBlank()) {
            output = context.defaultOutputPath("blast-hits.tsv");
        }
        Path outputParent = output.toAbsolutePath().getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
        }

        SequenceKindDetector kindDetector = new SequenceKindDetector();
        SequenceKind queryKind = kindDetector.detect(query);
        SequenceKind subjectKind = kindDetector.detect(subject);
        long inputQueries = countRecords(query);
        BlastProgram program = selectProgram(request.string("program"), queryKind, subjectKind);
        validateKinds(program, queryKind, subjectKind);

        Path blastBin = request.path("blastBin");
        ExecutableLocator locator = new ExecutableLocator(ExternalRuntime.appHome());
        ToolStatus databaseBuilder = locator.resolve(ExternalToolSpecs.MAKEBLASTDB, blastBin, null);
        ToolStatus blastStatus = locator.resolve(ExternalToolSpecs.blast(program.executable()), blastBin, null);
        requireAvailable(databaseBuilder, "makeblastdb");
        requireAvailable(blastStatus, program.executable());

        Duration commandTimeout = Duration.ofMinutes(Math.max(1, request.integer("timeoutMinutes")));
        ProcessRunner runner = new ProcessRunner();
        Path workDirectory = Files.createTempDirectory("opentools-blast-");
        Path stagedQuery = workDirectory.resolve("query.fasta");
        Files.copy(query, stagedQuery, StandardCopyOption.REPLACE_EXISTING);
        Path stagedHits = workDirectory.resolve("hits.tsv");
        context.log("BLAST program: " + program + " (query=" + queryKind + ", subject=" + subjectKind + ")");
        context.log("makeblastdb version: " + databaseBuilder.version());
        context.log(program.executable() + " version: " + blastStatus.version());

        boolean cacheEnabled = booleanValue(request, "cacheEnabled", true);
        boolean refreshDatabase = cacheEnabled && booleanValue(request, "refreshDatabase", false);
        BlastDatabaseCache.Limits cacheLimits = cacheLimits(request);
        BlastDatabaseCache.CachedDatabase cachedDatabase = null;
        String databaseArgument = BlastDatabaseCache.DATABASE_BASE;
        try {
            if (cacheEnabled) {
                Path cacheRoot = cacheRoot(request);
                String cacheKey = BlastDatabaseCache.key(
                        Hashing.sha256(subject),
                        databaseBuilder.version(),
                        program.name(),
                        program.subjectDatabaseType()
                );
                cachedDatabase = BlastDatabaseCache.acquire(
                        cacheRoot,
                        cacheKey,
                        program.subjectDatabaseType(),
                        refreshDatabase,
                        cacheLimits,
                        true,
                        context,
                        directory -> buildDatabase(
                                runner,
                                databaseBuilder,
                                subject,
                                directory,
                                program,
                                commandTimeout,
                                context
                        )
                );
                databaseArgument = BlastDatabaseCache.prepareForRuntime(
                        cachedDatabase,
                        workDirectory,
                        program.subjectDatabaseType()
                ).toString();
            } else {
                buildDatabase(
                        runner,
                        databaseBuilder,
                        subject,
                        workDirectory,
                        program,
                        commandTimeout,
                        context
                );
            }
        } catch (Exception ex) {
            TemporaryFiles.deleteRecursively(workDirectory);
            releaseCachedDatabase(cachedDatabase, cacheLimits, context);
            throw ex;
        }

        Files.deleteIfExists(stagedHits);
        List<String> blastCommand = new ArrayList<>();
        blastCommand.add(blastStatus.executable().toString());
        blastCommand.add("-query");
        blastCommand.add(stagedQuery.getFileName().toString());
        blastCommand.add("-db");
        blastCommand.add(databaseArgument);
        blastCommand.add("-out");
        blastCommand.add(stagedHits.getFileName().toString());
        blastCommand.add("-outfmt");
        blastCommand.add("6 qseqid sseqid pident length mismatch gapopen qstart qend sstart send evalue bitscore qlen slen");
        blastCommand.add("-evalue");
        blastCommand.add(Double.toString(decimal(request, "evalue", 1e-5)));
        blastCommand.add("-max_target_seqs");
        blastCommand.add(Integer.toString(positive(request, "maxTargetSeqs", 10)));
        blastCommand.add("-num_threads");
        blastCommand.add(Integer.toString(positive(request, "threads", 4)));
        blastCommand.addAll(ExternalArguments.parse(request.string("extraArgs")));

        try {
            ProcessResult blastResult = runner.run(
                    blastCommand,
                    workDirectory,
                    Map.of(),
                    commandTimeout,
                    context.cancellationToken(),
                    context::log
            );
            requireSuccess("BLAST_FAILED", program.executable(), blastResult);
            if (!Files.isRegularFile(stagedHits)) {
                Files.createFile(stagedHits);
            }
            moveOutput(stagedHits, output);
        } catch (Exception ex) {
            Files.deleteIfExists(stagedHits);
            throw ex;
        } finally {
            TemporaryFiles.deleteRecursively(workDirectory);
            releaseCachedDatabase(cachedDatabase, cacheLimits, context);
        }

        AlignmentTableSummary summary = new AlignmentTableParser().parse(output);
        Path summaryOutput = context.defaultOutputPath("blast-summary.tsv");
        AlignmentSummaryWriter.write(summaryOutput, summary);
        summary.metrics().put("program", program.name());
        summary.metrics().put("queryKind", queryKind.name());
        summary.metrics().put("subjectKind", subjectKind.name());
        summary.metrics().put("blastVersion", blastStatus.version());
        summary.metrics().put("makeblastdbVersion", databaseBuilder.version());
        summary.metrics().put("inputQueries", inputQueries);
        summary.metrics().put("queriesWithHits", summary.queryCount());
        summary.metrics().put("databaseCacheEnabled", cacheEnabled);
        summary.metrics().put("databaseCacheMaxMegabytes",
                cacheLimits.maxBytes() / (1024L * 1024L));
        summary.metrics().put("databaseCacheMaxEntries",
                cacheLimits.maxEntries());
        summary.metrics().put("refreshDatabaseRequested", refreshDatabase);
        summary.metrics().put("cacheHit", cachedDatabase != null && cachedDatabase.cacheHit());
        summary.metrics().put("databaseReused", cachedDatabase != null && cachedDatabase.databaseReused());
        summary.metrics().put("makeblastdbInvoked",
                !cacheEnabled || cachedDatabase == null || !cachedDatabase.cacheHit());
        if (cachedDatabase != null) {
            summary.metrics().put("databaseCacheKey", cachedDatabase.cacheKey());
            summary.metrics().put("databaseCacheDirectory", cachedDatabase.cacheRoot().toString());
            summary.metrics().put("databaseDirectory", cachedDatabase.databasePath().getParent().toString());
        }

        List<OutputArtifact> artifacts = List.of(
                new OutputArtifact(output, "BLAST tabular hits", "text/tab-separated-values"),
                new OutputArtifact(summaryOutput, "BLAST best-hit summary", "text/tab-separated-values")
        );
        context.progress(1.0, "BLAST completed");
        return new TaskResult(
                summary.totalHits() + " hits across " + inputQueries + " queries",
                "result.blast.summary",
                List.of(summary.totalHits(), inputQueries),
                program.name() + " using " + blastStatus.version(),
                "result.blast.detail",
                List.of(program.name(), blastStatus.version()),
                summary.resultTable(),
                artifacts,
                summary.metrics()
        );
    }

    private static BlastProgram selectProgram(String requested, SequenceKind query, SequenceKind subject) {
        if (requested != null && !requested.isBlank() && !requested.equalsIgnoreCase("AUTO")) {
            try {
                return BlastProgram.valueOf(requested.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new OpenToolException("INVALID_BLAST_PROGRAM", "Unknown BLAST program: " + requested, "Choose a supported BLAST program.");
            }
        }
        BlastProgram program = BlastProgram.auto(query, subject);
        if (program == null) {
            throw new OpenToolException(
                    "BLAST_PROGRAM_UNDETERMINED",
                    "Could not determine a BLAST program from the sequence alphabets.",
                    "Select BLASTN, BLASTP, BLASTX, TBLASTN, or TBLASTX explicitly."
            );
        }
        return program;
    }

    private static void validateKinds(BlastProgram program, SequenceKind query, SequenceKind subject) {
        boolean valid = switch (program) {
            case BLASTN -> query == SequenceKind.NUCLEOTIDE && subject == SequenceKind.NUCLEOTIDE;
            case BLASTP -> query == SequenceKind.PROTEIN && subject == SequenceKind.PROTEIN;
            case BLASTX -> query == SequenceKind.NUCLEOTIDE && subject == SequenceKind.PROTEIN;
            case TBLASTN -> query == SequenceKind.PROTEIN && subject == SequenceKind.NUCLEOTIDE;
            case TBLASTX -> query == SequenceKind.NUCLEOTIDE && subject == SequenceKind.NUCLEOTIDE;
        };
        if (!valid) {
            throw new OpenToolException(
                    "BLAST_KIND_MISMATCH",
                    program + " is incompatible with detected query=" + query + " and subject=" + subject + ".",
                    "Choose AUTO or a program matching the sequence types."
            );
        }
    }

    private static void requireAvailable(ToolStatus status, String name) {
        if (!status.available() || status.executable() == null) {
            throw new OpenToolException(
                    "EXTERNAL_TOOL_MISSING",
                    name + " was not found. " + status.diagnostic(),
                    "Install NCBI BLAST+, choose the BLAST bin directory, or set OPENTOOLS_"
                            + name.toUpperCase(java.util.Locale.ROOT) + "."
            );
        }
    }

    private static void requireSuccess(String code, String tool, ProcessResult result) {
        if (!result.succeeded()) {
            String tail = tail(result.output(), 3000);
            throw new ExternalProcessException(
                    code,
                    tool + " exited with code " + result.exitCode() + (tail.isBlank() ? "" : ": " + tail),
                    "Inspect run.log and validate the input files and BLAST parameters."
            );
        }
    }

    private static void moveOutput(Path temporary, Path output) throws Exception {
        try {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void buildDatabase(
            ProcessRunner runner,
            ToolStatus databaseBuilder,
            Path subject,
            Path directory,
            BlastProgram program,
            Duration timeout,
            TaskContext context
    ) throws Exception {
        Path stagedSubject = directory.resolve("subject.fasta");
        Files.copy(subject, stagedSubject, StandardCopyOption.REPLACE_EXISTING);
        try {
            List<String> command = List.of(
                    databaseBuilder.executable().toString(),
                    "-in", stagedSubject.getFileName().toString(),
                    "-dbtype", program.subjectDatabaseType(),
                    "-out", BlastDatabaseCache.DATABASE_BASE
            );
            ProcessResult result = runner.run(
                    command,
                    directory,
                    Map.of(),
                    timeout,
                    context.cancellationToken(),
                    context::log
            );
            requireSuccess("MAKEBLASTDB_FAILED", "makeblastdb", result);
        } finally {
            Files.deleteIfExists(stagedSubject);
        }
    }

    private static Path cacheRoot(ToolRequest request) throws Exception {
        Path configured = request.path("cacheDirectory");
        if (configured == null) {
            String property = System.getProperty("opentools.blast.cacheDir");
            configured = property == null || property.isBlank()
                    ? Path.of(System.getProperty("java.io.tmpdir"), "opentools", "blast-db-cache")
                    : Path.of(property);
        }
        Path root = configured.toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            if (!Files.isDirectory(root) || !Files.isWritable(root)) {
                throw new java.io.IOException("not a writable directory");
            }
            return root;
        } catch (Exception ex) {
            throw new OpenToolException(
                    "BLAST_DB_CACHE_UNAVAILABLE",
                    "BLAST database cache directory is not writable: " + root,
                    "Choose another cacheDirectory, disable cacheEnabled, or fix directory permissions.",
                    ex
            );
        }
    }

    private static int positive(ToolRequest request, String id, int fallback) {
        int value = request.integer(id);
        return value > 0 ? value : fallback;
    }

    private static BlastDatabaseCache.Limits cacheLimits(ToolRequest request) {
        int megabytes = request.integer("cacheMaxMegabytes");
        if (megabytes <= 0) {
            megabytes = BlastDatabaseCache.DEFAULT_MAX_MEGABYTES;
        }
        int entries = request.integer("cacheMaxEntries");
        if (entries <= 0) {
            entries = BlastDatabaseCache.DEFAULT_MAX_ENTRIES;
        }
        return new BlastDatabaseCache.Limits(
                megabytes * 1024L * 1024L,
                entries
        );
    }

    private static void releaseCachedDatabase(
            BlastDatabaseCache.CachedDatabase database,
            BlastDatabaseCache.Limits limits,
            TaskContext context
    ) {
        BlastDatabaseCache.release(database, limits, context);
    }

    private static double decimal(ToolRequest request, String id, double fallback) {
        Object value = request.value(id);
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean booleanValue(ToolRequest request, String id, boolean fallback) {
        Object value = request.value(id);
        if (value == null) {
            return fallback;
        }
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String tail(String value, int maximum) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() <= maximum ? trimmed : trimmed.substring(trimmed.length() - maximum);
    }

    private static long countRecords(Path fasta) throws Exception {
        long count = 0;
        try (io.opentools.formats.fasta.FastaReader reader = new io.opentools.formats.fasta.FastaReader(fasta)) {
            while (reader.next() != null) {
                count++;
            }
        }
        return count;
    }
}
