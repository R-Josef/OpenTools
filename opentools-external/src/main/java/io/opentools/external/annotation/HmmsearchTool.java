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

public final class HmmsearchTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "alignment.hmmer.hmmsearch",
            "HMMER hmmsearch",
            "Search protein sequences with a local HMM query using hmmsearch.",
            "Alignment",
            List.of("hmmsearch", "HMM query", "HMM 搜索蛋白"),
            List.of("HMM model", "Protein FASTA"),
            List.of("HMMER domtblout"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("hmm", "HMM query", "HMMER profile or HMM model file."),
                    ToolParameter.requiredFile("sequences", "Protein sequence database",
                            "Protein FASTA searched by the HMM query."),
                    ToolParameter.decimal("evalue", "E-value threshold",
                            "Maximum full-sequence E-value.", 1e-5, 0.0, 1e9),
                    ToolParameter.integer("threads", "Threads", "hmmsearch worker threads.", 4, 1, 256),
                    ToolParameter.integer("timeoutMinutes", "Timeout minutes",
                            "Maximum command runtime.", 30, 1, 1440),
                    ToolParameter.optionalDirectory("hmmerBin", "HMMER bin directory",
                            "Directory containing hmmsearch."),
                    ToolParameter.optionalOutputFile("domtbl", "domtblout file",
                            "Leave blank to save in the run directory."),
                    new ToolParameter("extraArgs", "Extra HMMER arguments",
                            "Quote-aware extra CLI arguments.", ParameterType.TEXT, false, "",
                            List.of(), null, null, true, null),
                    ToolParameter.bool("overwrite", "Overwrite output",
                            "Allow replacing an existing output.", false)
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
                    "domtbl",
                    "Output file already exists: " + output,
                    "Enable overwrite or choose a new path."
            )));
        }
        try {
            ExternalArguments.parse(request.string("extraArgs"));
        } catch (IllegalArgumentException ex) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "extraArgs", ex.getMessage(), "Close quoted arguments or remove them."
            )));
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path output = request.path("domtbl");
        if (output == null || output.toString().isBlank()) {
            output = context.defaultOutputPath("hmmsearch-domtbl.txt");
        }
        ToolStatus hmmsearch = new ExecutableLocator(ExternalRuntime.appHome()).resolve(
                ExternalToolSpecs.HMMSEARCH, request.path("hmmerBin"), null);
        if (!hmmsearch.available() || hmmsearch.executable() == null) {
            throw new OpenToolException(
                    "EXTERNAL_TOOL_MISSING",
                    "hmmsearch was not found. " + hmmsearch.diagnostic(),
                    "Install HMMER, provide hmmerBin, or set OPENTOOLS_HMMSEARCH."
            );
        }
        Path work = Files.createTempDirectory("opentools-hmmsearch-");
        try {
            Path hmm = work.resolve("query.hmm");
            Path sequences = work.resolve("sequences.fasta");
            Files.copy(request.path("hmm"), hmm, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(request.path("sequences"), sequences, StandardCopyOption.REPLACE_EXISTING);
            Path domtbl = work.resolve("hits.domtbl");
            List<String> command = command(hmmsearch.executable(), request, hmm, sequences, domtbl);
            ProcessResult result = new ProcessRunner().run(
                    command,
                    work,
                    Map.of(),
                    Duration.ofMinutes(Math.max(1, request.integer("timeoutMinutes"))),
                    context.cancellationToken(),
                    context::log
            );
            if (!result.succeeded()) {
                throw new ExternalProcessException(
                        "HMMER_FAILED",
                        "hmmsearch exited with code " + result.exitCode() + ": " + tail(result.output()),
                        "Inspect run.log and validate the HMM query and protein database."
                );
            }
            if (!Files.isRegularFile(domtbl)) {
                throw new ExternalProcessException(
                        "HMMER_OUTPUT_MISSING",
                        "hmmsearch did not create domtblout.",
                        "Check HMMER parameters and write permissions."
                );
            }
            FormatToolSupport.createParent(output);
            Files.move(domtbl, output, StandardCopyOption.REPLACE_EXISTING);
            List<HmmerDomainHit> hits = new HmmerDomTableParser().parse(output);
            context.progress(1.0, "HMMER hmmsearch completed");
            ResultTable table = new ResultTable(
                    List.of("Metric", "Value"),
                    List.of(List.of("Domain hits", Integer.toString(hits.size())))
            );
            return new ToolResult(
                    "hmmsearch reported " + hits.size() + " domain hits", List.of(hits.size()),
                    "hmmsearch " + hmmsearch.version(), List.of(hmmsearch.version()),
                    table,
                    List.of(new OutputArtifact(output, "HMMER hmmsearch domtblout", "text/plain")),
                    Map.of("domainHits", hits.size(), "hmmsearchVersion", hmmsearch.version(),
                            "searchMode", "HMMSEARCH")
            );
        } finally {
            TemporaryFiles.deleteRecursively(work);
        }
    }

    public static List<String> command(
            Path executable,
            ToolRequest request,
            Path hmm,
            Path sequences,
            Path domtbl
    ) {
        List<String> command = new ArrayList<>(List.of(
                executable.toString(),
                "--domtblout", domtbl.getFileName().toString(),
                "-E", Double.toString(number(request, "evalue", 1e-5)),
                "--cpu", Integer.toString(Math.max(1, request.integer("threads"))),
                hmm.getFileName().toString(),
                sequences.getFileName().toString()
        ));
        command.addAll(ExternalArguments.parse(request.string("extraArgs")));
        return command;
    }

    private static double number(ToolRequest request, String id, double fallback) {
        Object value = request.value(id);
        return value == null || String.valueOf(value).isBlank()
                ? fallback : Double.parseDouble(String.valueOf(value));
    }

    private static String tail(String output) {
        if (output == null) {
            return "";
        }
        return output.length() <= 3000 ? output : output.substring(output.length() - 3000);
    }
}
