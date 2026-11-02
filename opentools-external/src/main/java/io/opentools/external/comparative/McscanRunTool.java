package io.opentools.external.comparative;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.external.ExternalRuntime;
import io.opentools.external.process.ExternalProcessException;
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
import java.util.List;
import java.util.Map;

public final class McscanRunTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "mcscan.run", "Run MCScanX", "Run local MCScanX on prepared .gff and .blast files.",
            "Comparative genomics", List.of("MCScanX", "共线性分析"),
            List.of("MCScanX inputs"), List.of("collinearity"), "1.0.0",
            List.of(ToolParameter.string("prefix", "Input prefix", "Prefix of matching .gff and .blast files.", "mcscan"),
                    ToolParameter.integer("timeoutMinutes", "Timeout minutes", "Maximum runtime.", 60, 1, 1440),
                    ToolParameter.optionalFile("executablePath", "MCScanX executable", "Optional explicit executable."),
                    ToolParameter.optionalOutputFile("output", "Output collinearity", "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)));

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) {
        return validateParameters(request).merge(FormatToolSupport.validateOutput(request, Path.of(request.string("prefix") + ".gff")));
    }

    @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path sourceGff = Path.of(request.string("prefix") + ".gff").toAbsolutePath().normalize();
        Path sourceBlast = Path.of(request.string("prefix") + ".blast").toAbsolutePath().normalize();
        if (!Files.isRegularFile(sourceGff) || !Files.isRegularFile(sourceBlast)) {
            throw new OpenToolException("MCSCAN_INPUT_MISSING", "MCScanX .gff/.blast files were not found.",
                    "Run mcscan.input or provide a valid prefix.");
        }
        Path output = FormatToolSupport.outputPath(request, context, "mcscan.collinearity");
        ToolStatus tool = new ExecutableLocator(ExternalRuntime.appHome()).resolve(
                ExternalToolSpecs.MCSCANX, null, request.path("executablePath"));
        if (!tool.available() || tool.executable() == null) {
            throw new OpenToolException("EXTERNAL_TOOL_MISSING", "MCScanX was not found. " + tool.diagnostic(),
                    "Install MCScanX, set OPENTOOLS_MCSCANX, or provide executablePath.");
        }
        String versionLabel = tool.version() == null || tool.version().isBlank()
                ? "MCScanX (version command unavailable)" : tool.version();
        Path work = Files.createTempDirectory("opentools-mcscanx-");
        try {
            Files.copy(sourceGff, work.resolve("input.gff"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(sourceBlast, work.resolve("input.blast"), StandardCopyOption.REPLACE_EXISTING);
            var result = new ProcessRunner().run(List.of(tool.executable().toString(), "input"), work, Map.of(),
                    Duration.ofMinutes(Math.max(1, request.integer("timeoutMinutes"))),
                    context.cancellationToken(), context::log);
            if (!result.succeeded()) {
                throw new ExternalProcessException("MCSCAN_FAILED",
                        "MCScanX exited with code " + result.exitCode() + ": " + result.output(),
                        "Inspect run.log and validate inputs.");
            }
            Path collinearity = work.resolve("input.collinearity");
            if (!Files.isRegularFile(collinearity)) {
                throw new ExternalProcessException("MCSCAN_OUTPUT_MISSING", "MCScanX did not produce collinearity.",
                        "Check MCScanX input files.");
            }
            FormatToolSupport.createParent(output);
            Files.move(collinearity, output, StandardCopyOption.REPLACE_EXISTING);
            context.progress(1.0, "MCScanX completed");
            return new ToolResult("MCScanX collinearity completed", List.of(),
                    "MCScanX " + versionLabel, List.of(versionLabel),
                    io.opentools.api.tool.ResultTable.empty(),
                    List.of(new OutputArtifact(output, "Collinearity blocks", "text/plain")),
                    Map.of("version", versionLabel));
        } finally {
            TemporaryFiles.deleteRecursively(work);
        }
    }
}
