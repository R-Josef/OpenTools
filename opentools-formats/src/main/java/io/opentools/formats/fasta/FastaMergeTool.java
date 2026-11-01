package io.opentools.formats.fasta;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FastaMergeTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "fasta.merge",
            "Merge FASTA Files",
            "Merge multiple FASTA files in the listed order.",
            "Sequence",
            List.of("merge fasta", "concatenate fasta", "FASTA 合并"),
            List.of("FASTA", "FASTA.gz"),
            List.of("FASTA"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredText("inputs", "Input files", "One FASTA file path per line."),
                    ToolParameter.optionalOutputFile("output", "Output FASTA", "Leave blank to save inside the run directory."),
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
        List<String> paths = parsePaths(request.string("inputs"));
        if (paths.isEmpty()) {
            base = base.merge(ValidationResult.of(ValidationIssue.error(
                    "inputs", "No input files were provided.", "Add one FASTA path per line."
            )));
        }
        for (String value : paths) {
            Path path = Path.of(value);
            if (!Files.isRegularFile(path)) {
                base = base.merge(ValidationResult.of(ValidationIssue.error(
                        "inputs", "Input file does not exist: " + path, "Remove or correct this path."
                )));
            }
        }
        Path output = request.path("output");
        if (output != null && Files.exists(output) && !request.bool("overwrite")) {
            base = base.merge(ValidationResult.of(ValidationIssue.error(
                    "output", "Output file already exists: " + output, "Choose another path or enable overwrite."
            )));
        }
        return base;
    }

    @Override
    public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        List<Path> inputs = parsePaths(request.string("inputs")).stream().map(Path::of).toList();
        Path output = request.path("output");
        if (output == null || output.toString().isBlank()) {
            output = context.defaultOutputPath("merged.fa");
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        long records = 0;
        long bases = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (Path input : inputs) {
                context.cancellationToken().throwIfCancelled();
                context.log("Merging " + input);
                try (FastaReader reader = new FastaReader(input)) {
                    FastaRecord record;
                    while ((record = reader.next()) != null) {
                        context.cancellationToken().throwIfCancelled();
                        FastaWriter.write(writer, record, 80);
                        records++;
                        bases += record.sequence().length();
                    }
                }
            }
        } catch (Exception ex) {
            Files.deleteIfExists(output);
            throw ex;
        }
        if (records == 0) {
            Files.deleteIfExists(output);
            throw new OpenToolException("EMPTY_FASTA", "No FASTA records were found.", "Check the input files.");
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("files", inputs.size());
        metrics.put("sequences", records);
        metrics.put("totalBases", bases);
        ResultTable table = new ResultTable(
                List.of("Metric", "Value"),
                List.of(
                        List.of("Files", Integer.toString(inputs.size())),
                        List.of("Sequences", Long.toString(records)),
                        List.of("Total bases", Long.toString(bases))
                )
        );
        context.progress(1.0, "Merged " + inputs.size() + " files");
        return new TaskResult(
                "Merged " + inputs.size() + " files",
                "result.fasta.merge.summary",
                List.of(inputs.size()),
                records + " FASTA records",
                "result.fasta.merge.detail",
                List.of(records),
                table,
                List.of(new OutputArtifact(output, "Merged FASTA", "text/x-fasta")),
                metrics
        );
    }

    private static List<String> parsePaths(String text) {
        List<String> values = new ArrayList<>();
        if (text == null) {
            return values;
        }
        for (String line : text.lines().toList()) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                values.add(trimmed);
            }
        }
        return values;
    }
}
