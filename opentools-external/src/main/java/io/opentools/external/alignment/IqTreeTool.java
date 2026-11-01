package io.opentools.external.alignment;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.external.ExternalRuntime;
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

public final class IqTreeTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "alignment.iqtree", "IQ-TREE", "Run IQ-TREE maximum-likelihood phylogeny on a local alignment.",
            "Phylogeny", List.of("IQ-TREE", "phylogeny", "系统树"),
            List.of("FASTA alignment"), List.of("Newick"), "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input alignment", "Multiple sequence alignment."),
                    ToolParameter.string("model", "Substitution model", "IQ-TREE model or MFP.", "MFP"),
                    ToolParameter.integer("threads", "Threads", "Worker threads.", 4, 1, 256),
                    ToolParameter.bool("bootstrap", "Ultrafast bootstrap", "Run 1000 ultrafast bootstrap replicates.", false),
                    ToolParameter.integer("timeoutMinutes", "Timeout minutes", "Maximum runtime.", 240, 1, 10080),
                    ToolParameter.optionalFile("executablePath", "IQ-TREE executable", "Optional explicit executable."),
                    ToolParameter.optionalOutputFile("output", "Output tree", "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing tree.", false)
            ));

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) {
        return validateParameters(request).merge(FormatTaskSupport.validateOutput(request, request.path("input")));
    }

    @Override public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        Path output = FormatTaskSupport.outputPath(request, context, "iqtree.treefile");
        ToolStatus tool = new ExecutableLocator(ExternalRuntime.appHome()).resolve(
                ExternalToolSpecs.IQTREE, null, request.path("executablePath"));
        if (!tool.available() || tool.executable() == null) {
            throw new OpenToolException("EXTERNAL_TOOL_MISSING", "IQ-TREE was not found. " + tool.diagnostic(),
                    "Install IQ-TREE, set OPENTOOLS_IQTREE, or provide executablePath.");
        }
        Path work = Files.createTempDirectory("opentools-iqtree-");
        try {
            Files.copy(request.path("input"), work.resolve("alignment.fasta"), StandardCopyOption.REPLACE_EXISTING);
            List<String> command = new ArrayList<>(List.of(tool.executable().toString(),
                    "-s", "alignment.fasta", "-pre", "run", "-T", Integer.toString(request.integer("threads")),
                    "-m", request.string("model")));
            if (request.bool("bootstrap")) {
                command.addAll(List.of("-B", "1000"));
            }
            ProcessResult result = new ProcessRunner().run(command, work, Map.of(),
                    Duration.ofMinutes(Math.max(1, request.integer("timeoutMinutes"))),
                    context.cancellationToken(), context::log);
            if (!result.succeeded()) {
                throw new ExternalProcessException("IQTREE_FAILED",
                        "IQ-TREE exited with code " + result.exitCode() + ": " + tail(result.output()),
                        "Inspect run.log and verify the alignment.");
            }
            Path tree = work.resolve("run.treefile");
            if (!Files.isRegularFile(tree)) {
                throw new ExternalProcessException("IQTREE_OUTPUT_MISSING", "IQ-TREE did not create a treefile.",
                        "Check IQ-TREE output parameters.");
            }
            FormatTaskSupport.createParent(output);
            Files.move(tree, output, StandardCopyOption.REPLACE_EXISTING);
            context.progress(1.0, "IQ-TREE completed");
            return new TaskResult("IQ-TREE phylogeny completed", "result.iqtree.summary", List.of(),
                    "Model " + request.string("model") + "; " + tool.version(),
                    "result.iqtree.detail", List.of(tool.version()),
                    new ResultTable(List.of("Metric", "Value"), List.of(
                            List.of("Model", request.string("model")), List.of("Version", tool.version()))),
                    List.of(new OutputArtifact(output, "IQ-TREE Newick", "text/plain")),
                    Map.of("model", request.string("model"), "version", tool.version()));
        } finally {
            TemporaryFiles.deleteRecursively(work);
        }
    }

    private static String tail(String value) {
        return value == null ? "" : value.length() <= 3000 ? value : value.substring(value.length() - 3000);
    }
}
