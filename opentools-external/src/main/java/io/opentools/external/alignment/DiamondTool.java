package io.opentools.external.alignment;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
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
import java.util.List;
import java.util.Map;

public final class DiamondTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "alignment.diamond",
            "DIAMOND Protein Alignment",
            "Build a DIAMOND protein database and run a local blastp-style search.",
            "Alignment",
            List.of("DIAMOND", "diamond blastp", "蛋白快速比对"),
            List.of("Protein FASTA", "FASTA.gz"),
            List.of("DIAMOND tabular", "TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("query", "Query protein FASTA", "Protein query sequences."),
                    ToolParameter.requiredFile("subject", "Subject protein FASTA", "Protein sequences used to build a temporary DIAMOND database."),
                    ToolParameter.decimal("evalue", "E-value threshold", "Maximum E-value for reported hits.", 1e-5, 0.0, 1e9),
                    ToolParameter.integer("maxTargetSeqs", "Maximum target sequences", "Maximum subject hits per query.", 10, 1, 10_000),
                    ToolParameter.integer("threads", "Threads", "Number of DIAMOND worker threads.", 4, 1, 256),
                    ToolParameter.integer("timeoutMinutes", "Timeout minutes", "Maximum runtime for each external command.", 30, 1, 1440),
                    ToolParameter.bool("sensitive", "Sensitive mode", "Use DIAMOND --sensitive mode.", false),
                    ToolParameter.optionalFile("diamondPath", "DIAMOND executable", "Optional path to diamond.exe or diamond."),
                    ToolParameter.optionalOutputFile("output", "Output DIAMOND table", "Leave blank to save inside the run directory."),
                    new ToolParameter(
                            "extraArgs",
                            "Extra DIAMOND arguments",
                            "Optional quote-aware DIAMOND CLI arguments. They are passed without a shell.",
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
        long inputQueries = countRecords(query);
        SequenceKindDetector detector = new SequenceKindDetector();
        if (detector.detect(query) != SequenceKind.PROTEIN || detector.detect(subject) != SequenceKind.PROTEIN) {
            throw new OpenToolException(
                    "DIAMOND_PROTEIN_REQUIRED",
                    "DIAMOND blastp requires protein query and subject sequences.",
                    "Translate nucleotide CDS to protein before running DIAMOND."
            );
        }
        Path output = request.path("output");
        if (output == null || output.toString().isBlank()) {
            output = context.defaultOutputPath("diamond-hits.tsv");
        }
        Path outputParent = output.toAbsolutePath().getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
        }

        ToolStatus diamond = new ExecutableLocator(ExternalRuntime.appHome()).resolve(
                ExternalToolSpecs.DIAMOND,
                null,
                request.path("diamondPath")
        );
        if (!diamond.available() || diamond.executable() == null) {
            throw new OpenToolException(
                    "EXTERNAL_TOOL_MISSING",
                    "DIAMOND was not found. " + diamond.diagnostic(),
                    "Set OPENTOOLS_DIAMOND, provide diamondPath, or add DIAMOND to PATH."
            );
        }

        Path workDirectory = Files.createTempDirectory("opentools-diamond-");
        Path stagedQuery = workDirectory.resolve("query.fasta");
        Path stagedSubject = workDirectory.resolve("subject.fasta");
        Files.copy(query, stagedQuery, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(subject, stagedSubject, StandardCopyOption.REPLACE_EXISTING);
        Path stagedHits = workDirectory.resolve("hits.tsv");
        Duration timeout = Duration.ofMinutes(Math.max(1, request.integer("timeoutMinutes")));
        ProcessRunner runner = new ProcessRunner();
        context.log("DIAMOND version: " + diamond.version());

        ProcessResult makeDb;
        try {
            makeDb = runner.run(
                    List.of(
                            diamond.executable().toString(),
                            "makedb",
                            "--in", stagedSubject.getFileName().toString(),
                            "--db", "diamond-db",
                            "--threads", Integer.toString(positive(request, "threads", 4))
                    ),
                    workDirectory,
                    Map.of(),
                    timeout,
                    context.cancellationToken(),
                    context::log
            );
            requireSuccess("DIAMOND_MAKEDB_FAILED", "diamond makedb", makeDb);
        } catch (Exception ex) {
            TemporaryFiles.deleteRecursively(workDirectory);
            throw ex;
        }

        Files.deleteIfExists(stagedHits);
        List<String> command = new ArrayList<>(List.of(
                diamond.executable().toString(),
                "blastp",
                "--query", stagedQuery.getFileName().toString(),
                "--db", "diamond-db",
                "--out", stagedHits.getFileName().toString(),
                "--outfmt", "6",
                "qseqid", "sseqid", "pident", "length", "mismatch", "gapopen",
                "qstart", "qend", "sstart", "send", "evalue", "bitscore", "qlen", "slen",
                "--evalue", Double.toString(decimal(request, "evalue", 1e-5)),
                "--max-target-seqs", Integer.toString(positive(request, "maxTargetSeqs", 10)),
                "--threads", Integer.toString(positive(request, "threads", 4))
        ));
        if (request.bool("sensitive")) {
            command.add("--sensitive");
        }
        command.addAll(ExternalArguments.parse(request.string("extraArgs")));

        try {
            ProcessResult result = runner.run(
                    command,
                    workDirectory,
                    Map.of(),
                    timeout,
                    context.cancellationToken(),
                    context::log
            );
            requireSuccess("DIAMOND_FAILED", "diamond blastp", result);
            if (!Files.isRegularFile(stagedHits)) {
                Files.createFile(stagedHits);
            }
            moveOutput(stagedHits, output);
        } catch (Exception ex) {
            Files.deleteIfExists(stagedHits);
            throw ex;
        } finally {
            TemporaryFiles.deleteRecursively(workDirectory);
        }

        AlignmentTableSummary summary = new AlignmentTableParser().parse(output);
        Path summaryOutput = context.defaultOutputPath("diamond-summary.tsv");
        AlignmentSummaryWriter.write(summaryOutput, summary);
        summary.metrics().put("program", "DIAMOND");
        summary.metrics().put("diamondVersion", diamond.version());
        summary.metrics().put("inputQueries", inputQueries);
        summary.metrics().put("queriesWithHits", summary.queryCount());
        context.progress(1.0, "DIAMOND completed");
        return new TaskResult(
                summary.totalHits() + " hits across " + inputQueries + " queries",
                "result.diamond.summary",
                List.of(summary.totalHits(), inputQueries),
                "DIAMOND " + diamond.version(),
                "result.diamond.detail",
                List.of(diamond.version()),
                summary.resultTable(),
                List.of(
                        new OutputArtifact(output, "DIAMOND tabular hits", "text/tab-separated-values"),
                        new OutputArtifact(summaryOutput, "DIAMOND best-hit summary", "text/tab-separated-values")
                ),
                summary.metrics()
        );
    }

    private static void requireSuccess(String code, String tool, ProcessResult result) {
        if (!result.succeeded()) {
            throw new ExternalProcessException(
                    code,
                    tool + " exited with code " + result.exitCode() + ": " + tail(result.output(), 3000),
                    "Inspect run.log and validate protein inputs and DIAMOND parameters."
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

    private static int positive(ToolRequest request, String id, int fallback) {
        int value = request.integer(id);
        return value > 0 ? value : fallback;
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
