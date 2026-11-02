package io.opentools.formats.bed;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.core.util.OrderedParallelProcessor;
import io.opentools.formats.FormatToolSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BedStatsTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "bed.stats",
            "BED Statistics",
            "Summarize BED interval count, span, length, strands, and sequence IDs.",
            "Genome annotation",
            List.of("bed summary", "bed stats", "BED 统计"),
            List.of("BED", "BED.gz"),
            List.of("TSV"),
            "1.1.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input BED", "BED3 or BED3+, optionally gzip-compressed."),
                    ToolParameter.integer("threads", "Threads",
                            "0 uses AUTO for large files; 1 disables parallel record processing.",
                            0, 0, 64),
                    ToolParameter.optionalOutputFile("output", "Output TSV", "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        return validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("input")));
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path input = request.path("input");
        Path output = FormatToolSupport.outputPath(request, context, "bed-stats.tsv");
        int threads = OrderedParallelProcessor.resolveThreads(
                request.integer("threads"), Files.size(input));
        long[] totals = new long[8];
        FormatToolSupport.writeAtomically(output, writer -> {
            BedSummary summary = new BedSummary();
            try (BedReader reader = new BedReader(input)) {
                OrderedParallelProcessor.processChunks(
                        threads,
                        512,
                        context.cancellationToken(),
                        reader::next,
                        chunk -> {
                            BedSummary local = new BedSummary();
                            for (BedRecord record : chunk) {
                                local.add(record);
                            }
                            return local;
                        },
                        summary::merge
                );
            }
            long records = summary.records;
            long bases = summary.bases;
            long min = summary.min;
            long max = summary.max;
            long plus = summary.plus;
            long minus = summary.minus;
            long unstranded = summary.unstranded;
            Set<String> sequences = summary.sequences;
            if (records == 0) {
                throw new OpenToolException("EMPTY_BED", "No BED records were found.", "Check the input format.");
            }
            totals[0] = records;
            totals[1] = bases;
            totals[2] = min;
            totals[3] = max;
            totals[4] = sequences.size();
            totals[5] = plus;
            totals[6] = minus;
            totals[7] = unstranded;
            writer.write("metric\tvalue\n");
            writer.write("records\t" + records + "\n");
            writer.write("total_bases\t" + bases + "\n");
            writer.write("min_length\t" + min + "\n");
            writer.write("max_length\t" + max + "\n");
            writer.write("mean_length\t" + String.format(java.util.Locale.ROOT, "%.2f", bases / (double) records) + "\n");
            writer.write("sequence_ids\t" + sequences.size() + "\n");
            writer.write("plus_strand\t" + plus + "\n");
            writer.write("minus_strand\t" + minus + "\n");
            writer.write("unstranded\t" + unstranded + "\n");
        });
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("records", totals[0]);
        metrics.put("totalBases", totals[1]);
        metrics.put("minLength", totals[2]);
        metrics.put("maxLength", totals[3]);
        metrics.put("sequenceIds", totals[4]);
        metrics.put("plusStrand", totals[5]);
        metrics.put("minusStrand", totals[6]);
        metrics.put("unstranded", totals[7]);
        metrics.put("threads", threads);
        metrics.put("parallelRecords", threads > 1);
        context.progress(1.0, "BED statistics completed");
        return new ToolResult(
                "Summarized " + totals[0] + " BED intervals", List.of(totals[0]),
                totals[1] + " bases across " + totals[4] + " sequence IDs.", List.of(totals[1], totals[4]),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Records", Long.toString(totals[0])),
                        List.of("Total bases", Long.toString(totals[1])),
                        List.of("Min length", Long.toString(totals[2])),
                        List.of("Max length", Long.toString(totals[3])),
                        List.of("Sequence IDs", Long.toString(totals[4])),
                        List.of("Plus strand", Long.toString(totals[5])),
                        List.of("Minus strand", Long.toString(totals[6])),
                        List.of("Unstranded", Long.toString(totals[7]))
                )),
                List.of(new OutputArtifact(output, "BED statistics", "text/tab-separated-values")),
                metrics
        );
    }

    private static final class BedSummary {
        private long records;
        private long bases;
        private long min = Long.MAX_VALUE;
        private long max;
        private long plus;
        private long minus;
        private long unstranded;
        private final Set<String> sequences = new LinkedHashSet<>();

        private void add(BedRecord record) {
            records++;
            bases += record.length();
            min = Math.min(min, record.length());
            max = Math.max(max, record.length());
            sequences.add(record.seqId());
            if (record.strand() == '+') {
                plus++;
            } else if (record.strand() == '-') {
                minus++;
            } else {
                unstranded++;
            }
        }

        private void merge(BedSummary other) {
            records += other.records;
            bases += other.bases;
            min = Math.min(min, other.min);
            max = Math.max(max, other.max);
            plus += other.plus;
            minus += other.minus;
            unstranded += other.unstranded;
            sequences.addAll(other.sequences);
        }
    }
}
