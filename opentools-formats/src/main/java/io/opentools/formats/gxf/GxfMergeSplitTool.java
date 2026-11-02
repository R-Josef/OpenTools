package io.opentools.formats.gxf;

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
import io.opentools.formats.FormatToolSupport;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class GxfMergeSplitTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "gxf.merge-split",
            "Merge or Split Annotation",
            "Merge multiple GFF3/GTF files or split one file by sequence ID or feature type.",
            "Genome annotation",
            List.of("gff merge", "gff split", "gtf split", "注释合并", "注释拆分"),
            List.of("GFF3", "GTF", "GFF3.gz", "GTF.gz"),
            List.of("GFF3", "GTF", "TSV"),
            "1.1.0",
            List.of(
                    new ToolParameter("mode", "Mode", "Merge files or split one annotation.", ParameterType.ENUM,
                            false, "MERGE", List.of("MERGE", "SPLIT"), null, null, false, null),
                    ToolParameter.requiredText("inputs", "Input files",
                            "One GFF3/GTF path per line. SPLIT accepts exactly one path."),
                    ToolParameter.optionalOutputFile("output", "Merged output", "Required for MERGE mode."),
                    ToolParameter.string("outputDirectory", "Split output directory",
                            "Required for SPLIT mode. The directory is created when absent.", ""),
                    new ToolParameter("splitBy", "Split by", "Partition key for SPLIT mode.", ParameterType.ENUM,
                            false, "SEQ_ID", List.of("SEQ_ID", "TYPE"), null, null, false, null),
                    ToolParameter.integer("maxRecords", "Maximum split records",
                            "Safety bound for records buffered in SPLIT mode.",
                            2_000_000, 1, 100_000_000),
                    ToolParameter.bool("overwrite", "Overwrite outputs", "Allow replacing existing outputs.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request);
        List<String> paths = inputPaths(request.string("inputs"));
        if (paths.isEmpty()) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "inputs", "No annotation files were provided.", "Add one path per line.")));
        }
        for (String value : paths) {
            Path path = Path.of(value);
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                result = result.merge(ValidationResult.of(ValidationIssue.error(
                        "inputs", "File does not exist or is unreadable: " + path, "Choose readable annotation files.")));
            }
        }
        if ("MERGE".equals(request.string("mode"))) {
            Path output = request.path("output");
            if (output == null) {
                result = result.merge(ValidationResult.of(ValidationIssue.error(
                        "output", "Merged output is required.", "Choose an output GFF3/GTF path.")));
            } else {
                result = result.merge(FormatToolSupport.validateOutput(request, paths.isEmpty() ? null : Path.of(paths.getFirst())));
            }
        } else {
            if (paths.size() != 1) {
                result = result.merge(ValidationResult.of(ValidationIssue.error(
                        "inputs", "SPLIT mode accepts exactly one input file.", "Keep only one path.")));
            }
            if (request.string("outputDirectory").isBlank()) {
                result = result.merge(ValidationResult.of(ValidationIssue.error(
                        "outputDirectory", "Split output directory is required.", "Choose an output directory.")));
            }
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        List<Path> inputs = inputPaths(request.string("inputs")).stream().map(Path::of).toList();
        if ("MERGE".equals(request.string("mode"))) {
            return merge(inputs, request, context);
        }
        return split(inputs.getFirst(), request, context);
    }

    private static ToolResult merge(List<Path> inputs, ToolRequest request, ToolExecutionContext context) throws Exception {
        Path output = FormatToolSupport.outputPath(request, context, "merged.gff3");
        long[] totals = new long[2];
        FormatToolSupport.writeAtomically(output, writer -> {
            long records = 0;
            long files = 0;
            for (Path input : inputs) {
                try (GxfReader reader = new GxfReader(input)) {
                    GxfRecord record;
                    while ((record = reader.next()) != null) {
                        context.cancellationToken().throwIfCancelled();
                        GxfWriter.write(writer, record);
                        records++;
                    }
                }
                files++;
            }
            if (records == 0) {
                throw new OpenToolException("EMPTY_ANNOTATION", "No annotation records were found.", "Check inputs.");
            }
            totals[0] = files;
            totals[1] = records;
        });
        context.progress(1.0, "Annotation merge completed");
        return new ToolResult(
                "Merged " + totals[0] + " annotation files", List.of(totals[0]),
                totals[1] + " annotation records were written.", List.of(totals[1]),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Files", Long.toString(totals[0])),
                        List.of("Records", Long.toString(totals[1]))
                )),
                List.of(new OutputArtifact(output, "Merged annotation", "text/plain")),
                Map.of("files", totals[0], "records", totals[1])
        );
    }

    private static ToolResult split(Path input, ToolRequest request, ToolExecutionContext context) throws Exception {
        Path directory = Path.of(request.string("outputDirectory")).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Map<String, List<GxfRecord>> groups = new TreeMap<>();
        long recordsSeen = 0;
        try (GxfReader reader = new GxfReader(input)) {
            GxfRecord record;
            while ((record = reader.next()) != null) {
                if (recordsSeen >= request.integer("maxRecords")) {
                    throw new OpenToolException(
                            "ANNOTATION_MEMORY_BOUND",
                            "SPLIT input exceeds maxRecords.",
                            "Raise the bound or split the annotation in smaller partitions."
                    );
                }
                String key = "TYPE".equals(request.string("splitBy")) ? record.type() : record.seqId();
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(record);
                recordsSeen++;
            }
        }
        if (groups.isEmpty()) {
            throw new OpenToolException("EMPTY_ANNOTATION", "No annotation records were found.", "Check the input.");
        }
        List<OutputArtifact> artifacts = new ArrayList<>();
        Path manifest = directory.resolve("split-manifest.tsv");
        List<List<String>> rows = new ArrayList<>();
        try (BufferedWriter report = Files.newBufferedWriter(manifest, StandardCharsets.UTF_8)) {
            report.write("key\tpath\trecords\n");
            for (Map.Entry<String, List<GxfRecord>> entry : groups.entrySet()) {
                Path output = directory.resolve(FormatToolSupport.sanitizeFileName(entry.getKey()) + ".gff3");
                if (Files.exists(output) && !request.bool("overwrite")) {
                    throw new OpenToolException("OUTPUT_EXISTS", "Split output already exists: " + output,
                            "Choose another directory or enable overwrite.");
                }
                FormatToolSupport.writeAtomically(output, writer -> {
                    for (GxfRecord record : entry.getValue()) {
                        GxfWriter.write(writer, record);
                    }
                });
                artifacts.add(new OutputArtifact(output, "Annotation part: " + entry.getKey(), "text/plain"));
                rows.add(List.of(entry.getKey(), output.toString(), Integer.toString(entry.getValue().size())));
                report.write(escape(entry.getKey()));
                report.write('\t');
                report.write(escape(output.toString()));
                report.write('\t');
                report.write(Integer.toString(entry.getValue().size()));
                report.newLine();
            }
        }
        artifacts.add(new OutputArtifact(manifest, "Split manifest", "text/tab-separated-values"));
        context.progress(1.0, "Annotation split completed");
        long records = groups.values().stream().mapToLong(List::size).sum();
        return new ToolResult(
                "Split annotation into " + groups.size() + " parts", List.of(groups.size()),
                records + " records were partitioned by " + request.string("splitBy") + ".", List.of(records),
                new ResultTable(List.of("Key", "Path", "Records"), rows),
                artifacts,
                Map.of("parts", groups.size(), "records", records, "splitBy", request.string("splitBy"),
                        "memoryBound", request.integer("maxRecords"),
                        "annotationMemory", "bounded-groups")
        );
    }

    private static List<String> inputPaths(String text) {
        return text == null ? List.of() : text.lines()
                .map(String::trim)
                .filter(value -> !value.isEmpty() && !value.startsWith("#"))
                .toList();
    }

    private static String escape(String value) {
        return value.replace("\t", "\\t").replace("\n", "\\n");
    }
}
