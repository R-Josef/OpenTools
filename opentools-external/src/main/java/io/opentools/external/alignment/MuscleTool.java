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

public final class MuscleTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "alignment.muscle",
            "MUSCLE Alignment",
            "Run MUSCLE 5 or MUSCLE 3 on a local FASTA file without shell interpolation.",
            "Alignment",
            List.of("MUSCLE", "multiple sequence alignment", "多序列比对"),
            List.of("FASTA"),
            List.of("FASTA alignment"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input FASTA", "Un aligned FASTA sequences."),
                    new ToolParameter("version", "MUSCLE version", "Command-line syntax version.", ParameterType.ENUM,
                            false, "MUSCLE5", List.of("MUSCLE5", "MUSCLE3"), null, null, false, null),
                    ToolParameter.integer("threads", "Threads", "MUSCLE worker threads.", 4, 1, 256),
                    ToolParameter.integer("timeoutMinutes", "Timeout minutes", "Maximum command runtime.", 60, 1, 1440),
                    ToolParameter.optionalFile("executablePath", "MUSCLE executable", "Optional explicit executable path."),
                    ToolParameter.optionalOutputFile("output", "Output alignment", "Leave blank to save in the run directory."),
                    new ToolParameter("extraArgs", "Extra MUSCLE arguments", "Quote-aware extra CLI arguments.",
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
        Path output = FormatTaskSupport.outputPath(request, context, "muscle-alignment.fa");
        ToolStatus muscle = new ExecutableLocator(ExternalRuntime.appHome()).resolve(
                ExternalToolSpecs.MUSCLE, null, request.path("executablePath"));
        if (!muscle.available() || muscle.executable() == null) {
            throw new OpenToolException("EXTERNAL_TOOL_MISSING",
                    "MUSCLE was not found. " + muscle.diagnostic(),
                    "Install MUSCLE, provide executablePath, or set OPENTOOLS_MUSCLE.");
        }
        Path work = Files.createTempDirectory("opentools-muscle-");
        try {
            Path input = work.resolve("input.fasta");
            Path aligned = work.resolve("aligned.fasta");
            Files.copy(request.path("input"), input, StandardCopyOption.REPLACE_EXISTING);
            List<String> command = new ArrayList<>();
            command.add(muscle.executable().toString());
            if ("MUSCLE3".equals(request.string("version"))) {
                command.addAll(List.of("-in", input.getFileName().toString(),
                        "-out", aligned.getFileName().toString()));
            } else {
                command.addAll(List.of("-align", input.getFileName().toString(),
                        "-output", aligned.getFileName().toString()));
                if (request.integer("threads") > 1) {
                    command.addAll(List.of("-threads", Integer.toString(request.integer("threads"))));
                }
            }
            command.addAll(ExternalArguments.parse(request.string("extraArgs")));
            ProcessResult result = new ProcessRunner().run(
                    command, work, Map.of(),
                    Duration.ofMinutes(Math.max(1, request.integer("timeoutMinutes"))),
                    context.cancellationToken(), context::log);
            if (!result.succeeded()) {
                throw new ExternalProcessException("MUSCLE_FAILED",
                        "MUSCLE exited with code " + result.exitCode() + ": " + tail(result.output()),
                        "Inspect run.log and verify the selected MUSCLE syntax version.");
            }
            if (!Files.isRegularFile(aligned)) {
                throw new ExternalProcessException("MUSCLE_OUTPUT_MISSING",
                        "MUSCLE did not create an alignment.", "Check the output command syntax.");
            }
            FormatTaskSupport.createParent(output);
            Files.move(aligned, output, StandardCopyOption.REPLACE_EXISTING);
            context.progress(1.0, "MUSCLE alignment completed");
            return new TaskResult(
                    "MUSCLE alignment completed",
                    "result.muscle.summary", List.of(),
                    "Using " + muscle.version(),
                    "result.muscle.detail", List.of(muscle.version()),
                    new ResultTable(List.of("Metric", "Value"), List.of(
                            List.of("Version", muscle.version()),
                            List.of("Syntax", request.string("version"))
                    )),
                    List.of(new OutputArtifact(output, "MUSCLE alignment", "text/x-fasta")),
                    Map.of("muscleVersion", muscle.version(), "syntax", request.string("version"))
            );
        } finally {
            TemporaryFiles.deleteRecursively(work);
        }
    }

    private static String tail(String output) {
        return output == null ? "" : output.length() <= 3000 ? output : output.substring(output.length() - 3000);
    }
}
