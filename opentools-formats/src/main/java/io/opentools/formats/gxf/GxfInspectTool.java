package io.opentools.formats.gxf;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
import io.opentools.core.util.OrderedParallelProcessor;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GxfInspectTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "gxf.inspect",
            "Inspect GFF3/GTF",
            "Summarize annotation records, sequence IDs, and feature types.",
            "Genome annotation",
            List.of("gff stats", "gtf stats", "GFF 统计", "注释统计"),
            List.of("GFF3", "GTF", "GFF3.gz", "GTF.gz"),
            List.of("TSV"),
            "1.1.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input GFF3/GTF", "Annotation file to inspect."),
                    ToolParameter.integer("threads", "Threads",
                            "0 uses AUTO for large files; 1 disables parallel record processing.",
                            0, 0, 64),
                    ToolParameter.bool("skipInvalidLines", "Skip invalid lines",
                            "Record malformed lines in the summary instead of failing the whole inspection.", true),
                    ToolParameter.optionalOutputFile("output", "Output prefix", "Optional TSV file. Companion feature-type output is written beside it."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing existing custom output files.", false)
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
                    "output",
                    "Output file already exists: " + output,
                    "Choose another path or enable overwrite."
            )));
        }
        return base;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path input = request.path("input");
        Path output = request.path("output");
        if (output == null || output.toString().isBlank()) {
            output = context.defaultOutputPath("gxf-summary.tsv");
        }
        Path typeOutput = output.resolveSibling(stripExtension(output.getFileName().toString()) + "-features.tsv");
        createParent(output);
        int threads = OrderedParallelProcessor.resolveThreads(
                request.integer("threads"), Files.size(input));

        GxfSummary summary = new GxfSummary();
        List<String> skipped;
        try (GxfReader reader = new GxfReader(input, request.bool("skipInvalidLines"))) {
            OrderedParallelProcessor.processChunks(
                    threads,
                    512,
                    context.cancellationToken(),
                    reader::next,
                    chunk -> {
                        GxfSummary local = new GxfSummary();
                        for (GxfRecord record : chunk) {
                            local.add(record);
                        }
                        return local;
                    },
                    summary::merge
            );
            skipped = reader.warnings();
        }
        long records = summary.records;
        long totalLength = summary.totalLength;
        long geneCount = summary.geneCount;
        long transcriptCount = summary.transcriptCount;
        Set<String> sequenceIds = summary.sequenceIds;
        Map<String, Long> featureTypes = summary.featureTypes;
        if (records == 0) {
            throw new OpenToolException("EMPTY_GXF", "No annotation records were found.", "Check the input file.");
        }

        writeSummary(output, records, sequenceIds.size(), geneCount, transcriptCount, totalLength);
        writeFeatureTypes(typeOutput, featureTypes);

        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Records", Long.toString(records)));
        rows.add(List.of("Skipped invalid lines", Integer.toString(skipped.size())));
        rows.add(List.of("Sequence IDs", Integer.toString(sequenceIds.size())));
        rows.add(List.of("Genes", Long.toString(geneCount)));
        rows.add(List.of("Transcripts", Long.toString(transcriptCount)));
        rows.add(List.of("Total feature length", Long.toString(totalLength)));
        featureTypes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> rows.add(List.of("Feature: " + entry.getKey(), Long.toString(entry.getValue()))));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("records", records);
        metrics.put("sequenceIds", sequenceIds.size());
        metrics.put("genes", geneCount);
        metrics.put("transcripts", transcriptCount);
        metrics.put("featureTypes", featureTypes);
        metrics.put("threads", threads);
        metrics.put("parallelRecords", threads > 1);
        metrics.put("skippedLines", skipped.size());
        metrics.put("skippedLineDetails", skipped);
        context.progress(1.0, "GXF inspection completed");
        return new ToolResult(
                "Inspected " + records + " annotation records",
                List.of(records),
                sequenceIds.size() + " sequence IDs; " + featureTypes.size() + " feature types; "
                        + skipped.size() + " invalid lines skipped",
                List.of(sequenceIds.size(), featureTypes.size()),
                new ResultTable(List.of("Metric", "Value"), rows),
                List.of(
                        new OutputArtifact(output, "GXF summary", "text/tab-separated-values"),
                        new OutputArtifact(typeOutput, "GXF feature types", "text/tab-separated-values")
                ),
                metrics
        );
    }

    private static void writeSummary(
            Path output,
            long records,
            int sequenceIds,
            long genes,
            long transcripts,
            long totalLength
    ) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("metric\tvalue");
            writer.newLine();
            writeRow(writer, "records", records);
            writeRow(writer, "sequence_ids", sequenceIds);
            writeRow(writer, "genes", genes);
            writeRow(writer, "transcripts", transcripts);
            writeRow(writer, "total_feature_length", totalLength);
        }
    }

    private static void writeFeatureTypes(Path output, Map<String, Long> featureTypes) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("feature_type\tcount");
            writer.newLine();
            for (Map.Entry<String, Long> entry : featureTypes.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .toList()) {
                writer.write(entry.getKey());
                writer.write('\t');
                writer.write(Long.toString(entry.getValue()));
                writer.newLine();
            }
        }
    }

    private static void writeRow(BufferedWriter writer, String key, long value) throws Exception {
        writer.write(key);
        writer.write('\t');
        writer.write(Long.toString(value));
        writer.newLine();
    }

    private static void createParent(Path path) throws Exception {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static String stripExtension(String fileName) {
        int separator = fileName.lastIndexOf('.');
        return separator < 0 ? fileName : fileName.substring(0, separator);
    }

    private static final class GxfSummary {
        private long records;
        private long totalLength;
        private long geneCount;
        private long transcriptCount;
        private final Set<String> sequenceIds = new LinkedHashSet<>();
        private final Map<String, Long> featureTypes = new LinkedHashMap<>();

        private void add(GxfRecord record) {
            records++;
            totalLength += record.length();
            sequenceIds.add(record.seqId());
            featureTypes.merge(record.type(), 1L, Long::sum);
            String normalizedType = record.type().toLowerCase(Locale.ROOT);
            if (normalizedType.equals("gene")) {
                geneCount++;
            } else if (normalizedType.equals("mrna")
                    || normalizedType.equals("transcript")) {
                transcriptCount++;
            }
        }

        private void merge(GxfSummary other) {
            records += other.records;
            totalLength += other.totalLength;
            geneCount += other.geneCount;
            transcriptCount += other.transcriptCount;
            sequenceIds.addAll(other.sequenceIds);
            other.featureTypes.forEach((type, count) ->
                    featureTypes.merge(type, count, Long::sum));
        }
    }
}
