package io.opentools.formats.fasta;

import io.opentools.api.tool.AbstractOpenTool;
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
import io.opentools.api.tool.OpenToolException;
import io.opentools.core.util.OrderedParallelProcessor;
import io.opentools.formats.FormatToolSupport;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FastaStatsTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "fasta.stats",
            "FASTA Statistics",
            "Summarize sequence count, length, N50, gaps, and GC content.",
            "Sequence",
            List.of("fasta stats", "N50", "GC", "FASTA 统计", "序列统计"),
            List.of("FASTA", "FASTA.gz"),
            List.of("TSV"),
            "1.1.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input FASTA", "A FASTA file, optionally gzip-compressed."),
                    ToolParameter.integer("threads", "Threads",
                            "0 uses AUTO for large files; 1 disables parallel record processing.",
                            0, 0, 64),
                    ToolParameter.optionalOutputFile("output", "Output TSV", "Leave blank to save inside the run directory."),
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
            output = context.defaultOutputPath("fasta-stats.tsv");
        }
        context.log("Reading FASTA: " + input);

        long sequenceCount = 0;
        long totalBases = 0;
        long gcBases = 0;
        long acgtBases = 0;
        long nBases = 0;
        long gapBases = 0;
        List<Long> lengths = new ArrayList<>();
        long[] totals = new long[6];
        int threads = OrderedParallelProcessor.resolveThreads(
                request.integer("threads"), Files.size(input), 128L * 1024 * 1024);

        FormatToolSupport.writeAtomically(output, writer -> {
            writer.write("id\tlength\tgc_percent\tn_count\theader");
            writer.newLine();
            try (FastaReader reader = new FastaReader(input)) {
                OrderedParallelProcessor.processChunks(
                        threads,
                        32,
                        context.cancellationToken(),
                        reader::next,
                        chunk -> {
                            List<RecordStatistics> results = new ArrayList<>(chunk.size());
                            for (FastaRecord record : chunk) {
                                if (record.id().isBlank()) {
                                    throw new OpenToolException(
                                            "FASTA_EMPTY_ID",
                                            "A FASTA record has no identifier.",
                                            "Add a non-empty identifier after the '>' character."
                                    );
                                }
                                SequenceCounts counts = count(record.sequence());
                                results.add(new RecordStatistics(
                                        record,
                                        counts.length(),
                                        counts.gc(),
                                        counts.n(),
                                        counts.gap(),
                                        counts.canonical()
                                ));
                            }
                            return results;
                        },
                        chunkResult -> {
                            for (RecordStatistics stats : chunkResult) {
                                FastaRecord record = stats.record();
                                totals[0]++;
                                totals[1] += stats.length();
                                totals[2] += stats.gc();
                                totals[3] += stats.canonical();
                                totals[4] += stats.n();
                                totals[5] += stats.gap();
                                lengths.add(stats.length());
                                writer.write(record.id());
                                writer.write('\t');
                                writer.write(Long.toString(stats.length()));
                                writer.write('\t');
                                writer.write(formatPercent(stats.gc(), stats.length()));
                                writer.write('\t');
                                writer.write(Long.toString(stats.n()));
                                writer.write('\t');
                                writer.write(record.header());
                                writer.newLine();
                                if (totals[0] % 1000 == 0) {
                                    context.log("Processed " + totals[0] + " sequences");
                                }
                            }
                        }
                );
            }
            if (totals[0] == 0) {
                throw new OpenToolException(
                        "EMPTY_FASTA",
                        "No FASTA records were found.",
                        "Check that the input starts with '>' headers."
                );
            }
        });
        sequenceCount = totals[0];
        totalBases = totals[1];
        gcBases = totals[2];
        acgtBases = totals[3];
        nBases = totals[4];
        gapBases = totals[5];

        long n50 = calculateN50(lengths, totalBases);
        ResultTable table = new ResultTable(
                List.of("Metric", "Value"),
                List.of(
                        List.of("Sequences", Long.toString(sequenceCount)),
                        List.of("Total bases", Long.toString(totalBases)),
                        List.of("N50", Long.toString(n50)),
                        List.of("GC percent", formatPercent(gcBases, acgtBases)),
                        List.of("N bases", Long.toString(nBases)),
                        List.of("Gap bases", Long.toString(gapBases))
                )
        );
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("sequences", sequenceCount);
        metrics.put("totalBases", totalBases);
        metrics.put("n50", n50);
        metrics.put("gcPercent", parsePercent(formatPercent(gcBases, acgtBases)));
        metrics.put("nBases", nBases);
        metrics.put("gapBases", gapBases);
        metrics.put("threads", threads);
        metrics.put("parallelRecords", threads > 1);

        context.progress(1.0, "FASTA statistics completed");
        return new ToolResult(
                "Processed " + sequenceCount + " sequences",
                "result.fasta.stats.summary",
                List.of(sequenceCount),
                "Total bases: " + totalBases + "; N50: " + n50,
                "result.fasta.stats.detail",
                List.of(totalBases, n50),
                table,
                List.of(new OutputArtifact(output, "FASTA statistics", "text/tab-separated-values")),
                metrics
        );
    }

    private static long calculateN50(List<Long> lengths, long totalBases) {
        lengths.sort(Comparator.reverseOrder());
        long cumulative = 0;
        long half = (totalBases + 1) / 2;
        for (long length : lengths) {
            cumulative += length;
            if (cumulative >= half) {
                return length;
            }
        }
        return 0;
    }

    private static SequenceCounts count(String sequence) {
        long gc = 0;
        long n = 0;
        long gaps = 0;
        long canonical = 0;
        for (int i = 0; i < sequence.length(); i++) {
            char current = Character.toUpperCase(sequence.charAt(i));
            switch (current) {
                case 'G', 'C' -> gc++;
                case 'N' -> n++;
                case '-', '.' -> gaps++;
                default -> {
                }
            }
            if (current == 'A' || current == 'C' || current == 'G'
                    || current == 'T' || current == 'U') {
                canonical++;
            }
        }
        return new SequenceCounts(sequence.length(), gc, n, gaps, canonical);
    }

    private static String formatPercent(long numerator, long denominator) {
        if (denominator <= 0) {
            return "0.00";
        }
        return String.format(Locale.ROOT, "%.2f", numerator * 100.0 / denominator);
    }

    private static double parsePercent(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private record SequenceCounts(
            long length,
            long gc,
            long n,
            long gap,
            long canonical
    ) {
    }

    private record RecordStatistics(
            FastaRecord record,
            long length,
            long gc,
            long n,
            long gap,
            long canonical
    ) {
    }
}
