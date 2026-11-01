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
import io.opentools.external.ExternalRuntime;
import io.opentools.external.process.ExternalArguments;
import io.opentools.external.process.ExternalProcessException;
import io.opentools.external.process.ProcessResult;
import io.opentools.external.process.ProcessRunner;
import io.opentools.external.process.TemporaryFiles;
import io.opentools.external.tool.ExecutableLocator;
import io.opentools.external.tool.ExternalToolSpecs;
import io.opentools.external.tool.ToolStatus;
import io.opentools.formats.FormatTaskSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TrimalTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "alignment.trim",
            "trimAl Alignment Trimming",
            "Trim a multiple sequence alignment with trimAl automated or gap-threshold modes.",
            "Alignment",
            List.of("trimAl", "alignment trim", "比对修剪"),
            List.of("FASTA alignment"),
            List.of("FASTA alignment"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input alignment", "Multiple sequence alignment in FASTA."),
                    new ToolParameter("method", "Trimming method", "Algorithm passed to trimAl.", ParameterType.ENUM,
                            false, "AUTOMATED1", List.of("AUTOMATED1", "GT", "STRICT", "STRICTPLUS"),
                            null, null, false, null),
                    ToolParameter.decimal("gapThreshold", "Gap threshold", "Gap fraction for GT mode.", 0.5, 0.0, 1.0),
                    ToolParameter.decimal("minimumCoverage", "Minimum coverage",
                            "Optional minimum residue coverage passed to trimAl -cons.", 0.0, 0.0, 1.0),
                    ToolParameter.integer("timeoutMinutes", "Timeout minutes", "Maximum command runtime.", 30, 1, 1440),
                    ToolParameter.optionalFile("executablePath", "trimAl executable", "Optional explicit executable path."),
                    ToolParameter.optionalOutputFile("output", "Output alignment", "Leave blank to save in the run directory."),
                    new ToolParameter("extraArgs", "Extra trimAl arguments", "Quote-aware extra CLI arguments.",
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
        ValidationResult result = validateParameters(request)
                .merge(FormatTaskSupport.validateOutput(request, request.path("input")));
        try {
            ExternalArguments.parse(request.string("extraArgs"));
        } catch (IllegalArgumentException ex) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "extraArgs", ex.getMessage(), "Close quoted arguments or remove them.")));
        }
        return result;
    }

    @Override
    public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        Path output = FormatTaskSupport.outputPath(request, context, "trimmed-alignment.fa");
        ToolStatus trimal = new ExecutableLocator(ExternalRuntime.appHome()).resolve(
                ExternalToolSpecs.TRIMAL, null, request.path("executablePath"));
        if (!trimal.available() || trimal.executable() == null) {
            throw new OpenToolException("EXTERNAL_TOOL_MISSING",
                    "trimAl was not found. " + trimal.diagnostic(),
                    "Install trimAl, provide executablePath, or set OPENTOOLS_TRIMAL.");
        }
        Path work = Files.createTempDirectory("opentools-trimal-");
        try {
            Path input = work.resolve("input.fasta");
            Path aligned = work.resolve("trimmed.fasta");
            Files.copy(request.path("input"), input, StandardCopyOption.REPLACE_EXISTING);
            List<String> command = command(trimal.executable(), request, input, aligned);
            ProcessResult result = new ProcessRunner().run(
                    command, work, Map.of(),
                    Duration.ofMinutes(Math.max(1, request.integer("timeoutMinutes"))),
                    context.cancellationToken(), context::log);
            if (!result.succeeded()) {
                throw new ExternalProcessException("TRIMAL_FAILED",
                        "trimAl exited with code " + result.exitCode() + ": " + tail(result.output()),
                        "Inspect run.log and validate the alignment and trimming parameters.");
            }
            if (!Files.isRegularFile(aligned)) {
                throw new ExternalProcessException("TRIMAL_OUTPUT_MISSING",
                        "trimAl did not create an output alignment.", "Check the trimAl parameters.");
            }
            FormatTaskSupport.createParent(output);
            Files.move(aligned, output, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            TemporaryFiles.deleteRecursively(work);
        }
        context.progress(1.0, "trimAl completed");
        return new TaskResult(
                "trimAl alignment trimming completed",
                "result.trimal.summary", List.of(),
                "Using " + trimal.version(),
                "result.trimal.detail", List.of(trimal.version()),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Method", request.string("method")),
                        List.of("Version", trimal.version())
                )),
                List.of(new OutputArtifact(output, "Trimmed alignment", "text/x-fasta")),
                Map.of("method", request.string("method"), "trimalVersion", trimal.version())
        );
    }

    static List<String> command(Path executable, ToolRequest request, Path input, Path output) {
        List<String> command = new ArrayList<>(List.of(
                executable.toString(), "-in", input.toString(), "-out", output.toString()
        ));
        switch (request.string("method")) {
            case "AUTOMATED1" -> command.add("-automated1");
            case "STRICT" -> command.add("-strict");
            case "STRICTPLUS" -> command.add("-strictplus");
            default -> {
                command.add("-gt");
                command.add(Double.toString(decimal(request, "gapThreshold", 0.5)));
            }
        }
        double coverage = decimal(request, "minimumCoverage", 0.0);
        if (coverage > 0) {
            command.add("-cons");
            command.add(Double.toString(coverage));
        }
        command.addAll(ExternalArguments.parse(request.string("extraArgs")));
        return command;
    }

    private static double decimal(ToolRequest request, String id, double fallback) {
        Object value = request.value(id);
        return value == null || String.valueOf(value).isBlank()
                ? fallback : Double.parseDouble(String.valueOf(value));
    }

    private static String tail(String output) {
        return output == null ? "" : output.length() <= 3000 ? output : output.substring(output.length() - 3000);
    }
}
