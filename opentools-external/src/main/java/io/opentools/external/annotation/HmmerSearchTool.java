package io.opentools.external.annotation;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
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
import io.opentools.external.tool.ExecutableLocator;
import io.opentools.external.tool.ExternalToolSpecs;
import io.opentools.external.tool.ToolStatus;
import io.opentools.formats.FormatToolSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class HmmerSearchTool extends AbstractOpenTool {
    private static final List<String> PRESSED_DATABASE_SUFFIXES =
            List.of(".h3f", ".h3i", ".h3m", ".h3p");
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "alignment.hmmer.search",
            "HMMER hmmscan Search",
            "Search protein FASTA sequences against a local HMM database with hmmscan.",
            "Alignment",
            List.of("hmmscan", "HMMER search", "HMMER 搜索"),
            List.of("Protein FASTA", "HMM database"),
            List.of("HMMER domtblout"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("sequences", "Protein FASTA", "Protein sequences to scan."),
                    ToolParameter.requiredFile("database", "HMM database", "HMMER profile database."),
                    ToolParameter.decimal("evalue", "E-value threshold", "Maximum full-sequence E-value.", 1e-5, 0.0, 1e9),
                    ToolParameter.integer("threads", "Threads", "hmmscan worker threads.", 4, 1, 256),
                    ToolParameter.integer("timeoutMinutes", "Timeout minutes", "Maximum command runtime.", 30, 1, 1440),
                    ToolParameter.optionalDirectory("hmmerBin", "HMMER bin directory", "Directory containing hmmscan."),
                    ToolParameter.optionalOutputFile("domtbl", "domtblout file", "Leave blank to save in the run directory."),
                    new ToolParameter("extraArgs", "Extra HMMER arguments", "Quote-aware extra CLI arguments.",
                            ParameterType.TEXT, false, "", List.of(), null, null, true, null),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request);
        Path output = request.path("domtbl");
        if (output != null && Files.exists(output) && !request.bool("overwrite")) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "domtbl", "Output file already exists: " + output, "Enable overwrite or choose a new path.")));
        }
        try {
            ExternalArguments.parse(request.string("extraArgs"));
        } catch (IllegalArgumentException ex) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "extraArgs", ex.getMessage(), "Close quoted arguments or remove them.")));
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path output = request.path("domtbl");
        if (output == null || output.toString().isBlank()) {
            output = context.defaultOutputPath("hmmer-domtbl.txt");
        }
        ToolStatus hmmscan = new ExecutableLocator(ExternalRuntime.appHome()).resolve(
                ExternalToolSpecs.HMMSCAN, request.path("hmmerBin"), null);
        if (!hmmscan.available() || hmmscan.executable() == null) {
            throw new OpenToolException("EXTERNAL_TOOL_MISSING",
                    "hmmscan was not found. " + hmmscan.diagnostic(),
                    "Install HMMER, provide hmmerBin, or set OPENTOOLS_HMMSCAN.");
        }
        Path work = Files.createTempDirectory("opentools-hmmer-");
        try {
            Path sequences = work.resolve("sequences.fasta");
            Path database = work.resolve("profiles.hmm");
            Files.copy(request.path("sequences"), sequences, StandardCopyOption.REPLACE_EXISTING);
            copyPressedDatabase(request.path("database"), database);
            Path domtbl = work.resolve("hits.domtbl");
            List<String> command = new ArrayList<>(List.of(
                    hmmscan.executable().toString(),
                    "--domtblout", domtbl.getFileName().toString(),
                    "-E", Double.toString(decimal(request, "evalue", 1e-5)),
                    "--cpu", Integer.toString(Math.max(1, request.integer("threads"))),
                    database.getFileName().toString(),
                    sequences.getFileName().toString()
            ));
            command.addAll(ExternalArguments.parse(request.string("extraArgs")));
            ProcessResult result = new ProcessRunner().run(
                    command, work, Map.of(),
                    Duration.ofMinutes(Math.max(1, request.integer("timeoutMinutes"))),
                    context.cancellationToken(), context::log);
            if (!result.succeeded()) {
                throw new ExternalProcessException("HMMER_FAILED",
                        "hmmscan exited with code " + result.exitCode() + ": " + tail(result.output()),
                        "Inspect run.log and validate the HMM database and sequences.");
            }
            if (!Files.isRegularFile(domtbl)) {
                throw new ExternalProcessException("HMMER_OUTPUT_MISSING",
                        "hmmscan did not create domtblout.", "Check HMMER parameters and write permissions.");
            }
            FormatToolSupport.createParent(output);
            Files.move(domtbl, output, StandardCopyOption.REPLACE_EXISTING);
            List<HmmerDomainHit> hits = new HmmerDomTableParser().parse(output);
            context.progress(1.0, "HMMER search completed");
            ResultTable table = new ResultTable(
                    List.of("Metric", "Value"),
                    List.of(List.of("Domain hits", Integer.toString(hits.size())))
            );
            return new ToolResult(
                    "HMMER reported " + hits.size() + " domain hits",
                    "result.hmmer.summary", List.of(hits.size()),
                    "hmmscan " + hmmscan.version(),
                    "result.hmmer.detail", List.of(hmmscan.version()),
                    table,
                    List.of(new OutputArtifact(output, "HMMER domtblout", "text/plain")),
                    Map.of("domainHits", hits.size(), "hmmscanVersion", hmmscan.version())
            );
        } finally {
            TemporaryFiles.deleteRecursively(work);
        }
    }

    private static double decimal(ToolRequest request, String id, double fallback) {
        Object value = request.value(id);
        return value == null || String.valueOf(value).isBlank()
                ? fallback : Double.parseDouble(String.valueOf(value));
    }

    public static void copyPressedDatabase(Path source, Path target) throws Exception {
        List<Path> missing = new ArrayList<>();
        for (String suffix : PRESSED_DATABASE_SUFFIXES) {
            Path sidecar = source.resolveSibling(source.getFileName() + suffix);
            if (!Files.isRegularFile(sidecar)) {
                missing.add(sidecar);
            }
        }
        if (!missing.isEmpty()) {
            throw new OpenToolException(
                    "HMMER_DATABASE_NOT_PRESSED",
                    "Pressed HMMER database sidecars are missing: " + missing,
                    "Run hmmpress on the database before using hmmscan."
            );
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        for (String suffix : PRESSED_DATABASE_SUFFIXES) {
            Path sourceSidecar = source.resolveSibling(source.getFileName() + suffix);
            Path targetSidecar = target.resolveSibling(target.getFileName() + suffix);
            Files.copy(sourceSidecar, targetSidecar, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String tail(String output) {
        if (output == null) {
            return "";
        }
        return output.length() <= 3000 ? output : output.substring(output.length() - 3000);
    }
}
