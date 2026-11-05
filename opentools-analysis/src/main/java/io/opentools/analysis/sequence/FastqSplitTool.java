package io.opentools.analysis.sequence;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.fastq.FastqReader;
import io.opentools.formats.fastq.FastqRecord;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FastqSplitTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "fastq.split",
            "Split FASTQ File",
            "Split a FASTQ file into parts with a fixed number of records, preserving sequence and quality.",
            "Sequence",
            List.of("split fastq", "FASTQ 拆分"),
            List.of("FASTQ", "FASTQ.gz"),
            List.of("FASTQ"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input FASTQ", "The FASTQ file to split."),
                    ToolParameter.requiredInteger(
                            "recordsPerFile",
                            "Records per file",
                            "Number of FASTQ records in each output part.",
                            1000,
                            1,
                            10_000_000
                    ),
                    ToolParameter.optionalDirectory(
                            "outputDirectory",
                            "Output directory",
                            "Leave blank to save parts inside the run directory."
                    ),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing existing part files.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        return validateParameters(request);
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path input = request.path("input");
        int recordsPerFile = request.integer("recordsPerFile");
        Path outputDirectory = request.path("outputDirectory");
        if (outputDirectory == null || outputDirectory.toString().isBlank()) {
            outputDirectory = context.outputDirectory();
        }
        Files.createDirectories(outputDirectory);

        List<OutputArtifact> artifacts = new ArrayList<>();
        int part = 1;
        int recordsInPart = 0;
        long totalRecords = 0;
        BufferedWriter writer = null;
        try (FastqReader reader = new FastqReader(input)) {
            FastqRecord record;
            while ((record = reader.next()) != null) {
                context.cancellationToken().throwIfCancelled();
                if (writer == null || recordsInPart >= recordsPerFile) {
                    if (writer != null) {
                        writer.close();
                    }
                    Path partFile = outputDirectory.resolve(String.format(Locale.ROOT, "part-%04d.fastq", part));
                    if (Files.exists(partFile) && !request.bool("overwrite")) {
                        throw new OpenToolException(
                                "OUTPUT_EXISTS",
                                "Output part already exists: " + partFile,
                                "Choose another directory or enable overwrite."
                        );
                    }
                    writer = Files.newBufferedWriter(partFile, StandardCharsets.UTF_8);
                    artifacts.add(new OutputArtifact(partFile, "FASTQ part " + part, "text/x-fastq"));
                    part++;
                    recordsInPart = 0;
                }
                writer.write('@');
                writer.write(record.header());
                writer.newLine();
                writer.write(record.sequence());
                writer.newLine();
                writer.write('+');
                writer.newLine();
                writer.write(record.quality());
                writer.newLine();
                recordsInPart++;
                totalRecords++;
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
        if (totalRecords == 0) {
            throw new OpenToolException("EMPTY_FASTQ", "No FASTQ records were found.", "Check the input file.");
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("records", totalRecords);
        metrics.put("parts", artifacts.size());
        metrics.put("recordsPerFile", recordsPerFile);
        ResultTable table = new ResultTable(
                List.of("Metric", "Value"),
                List.of(
                        List.of("Records", Long.toString(totalRecords)),
                        List.of("Parts", Integer.toString(artifacts.size())),
                        List.of("Records per file", Integer.toString(recordsPerFile))
                )
        );
        context.progress(1.0, "Split into " + artifacts.size() + " files");
        return new ToolResult(
                "Split " + totalRecords + " FASTQ records",
                List.of(totalRecords),
                "Created " + artifacts.size() + " FASTQ files.",
                List.of(artifacts.size()),
                table,
                artifacts,
                metrics
        );
    }
}
