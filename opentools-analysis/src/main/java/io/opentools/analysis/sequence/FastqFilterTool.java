package io.opentools.analysis.sequence;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationIssue;
import io.opentools.api.tool.ValidationResult;
import io.opentools.core.util.OrderedParallelProcessor;
import io.opentools.formats.FormatToolSupport;
import io.opentools.formats.fastq.FastqReader;
import io.opentools.formats.fastq.FastqRecord;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FastqFilterTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "fastq.filter",
            "Filter FASTQ",
            "Filter and trim FASTQ records by length, mean quality, and N content with per-record reasons.",
            "Sequence",
            List.of("FASTQ filter", "trim FASTQ", "FASTQ 过滤", "质量过滤"),
            List.of("FASTQ", "FASTQ.gz"),
            List.of("FASTQ", "FASTQ.gz", "TSV report"),
            "1.2.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input FASTQ", "FASTQ, optionally gzip-compressed."),
                    ToolParameter.integer("threads", "Threads",
                            "0 uses AUTO for large files; 1 disables parallel read processing.",
                            0, 0, 64),
                    ToolParameter.integer("trimLeft", "Trim left", "Bases removed from the 5' end.", 0, 0, 10_000_000),
                    ToolParameter.integer("trimRight", "Trim right", "Bases removed from the 3' end.", 0, 0, 10_000_000),
                    ToolParameter.integer("minLength", "Minimum length", "Minimum retained read length.", 0, 0, 100_000_000),
                    ToolParameter.integer("maxLength", "Maximum length", "Maximum retained read length.", Integer.MAX_VALUE, 0, Integer.MAX_VALUE),
                    ToolParameter.decimal("minMeanQuality", "Minimum mean quality",
                            "Minimum mean Phred+33 quality after trimming.", 0.0, 0.0, 100.0),
                    ToolParameter.decimal("maxNPercent", "Maximum N percent",
                            "Maximum N percentage after trimming.", 100.0, 0.0, 100.0),
                    new ToolParameter(
                            "qualityEncoding", "Quality encoding",
                            "Encoding used to interpret quality characters. AUTO scans the complete file before filtering.",
                            ParameterType.ENUM, false, "PHRED_33",
                            List.of("AUTO", "PHRED_33", "PHRED_64", "SOLEXA_64"),
                            null, null, false, null),
                    ToolParameter.optionalOutputFile("output", "Output FASTQ",
                            "Leave blank to save in the run directory. A .gz suffix enables gzip."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)
            )
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("input")));
        if (request.integer("minLength") > request.integer("maxLength")) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "minLength", "Minimum length exceeds maximum length.", "Adjust length thresholds.")));
        }
        return result;
    }

    @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        FastqQualityEncoding.Resolution encoding = FastqQualityEncoding.resolve(
                request.path("input"), request.string("qualityEncoding"));
        int threads = OrderedParallelProcessor.resolveThreads(
                request.integer("threads"), Files.size(request.path("input")), 32L * 1024 * 1024);
        Path output = FormatToolSupport.outputPath(request, context, "filtered.fastq");
        Path report = context.defaultOutputPath("fastq-filter-report.tsv");
        long[] totals = new long[3];
        FormatToolSupport.writePairAtomically(output, report, (writer, reportWriter) -> {
            reportWriter.write("record\tid\tstatus\treason\tinput_length\toutput_length"
                    + "\tmean_quality\tn_percent\tquality_encoding\n");
            try (FastqReader reader = new FastqReader(request.path("input"))) {
                long[] sourceIndex = new long[1];
                OrderedParallelProcessor.process(
                        threads,
                        128,
                        context.cancellationToken(),
                        () -> {
                            FastqRecord record = reader.next();
                            return record == null
                                    ? null
                                    : new NumberedRead(++sourceIndex[0], record);
                        },
                        numbered -> filterRead(numbered, request, encoding.encoding()),
                        result -> {
                            totals[0]++;
                            FastqRecord record = result.read().record();
                            if (result.keep()) {
                                writer.write('@');
                                writer.write(record.header());
                                writer.newLine();
                                writer.write(result.sequence());
                                writer.newLine();
                                writer.write("+");
                                writer.newLine();
                                writer.write(result.quality());
                                writer.newLine();
                                totals[1]++;
                            } else {
                                totals[2]++;
                            }
                            reportWriter.write(totals[0] + "\t" + escape(record.id()) + "\t"
                                    + (result.keep() ? "KEPT" : "FILTERED") + "\t"
                                    + result.reason() + "\t"
                                    + record.sequence().length() + "\t"
                                    + result.sequence().length() + "\t"
                                    + format(result.meanQuality()) + "\t"
                                    + format(result.nPercent()) + "\t"
                                    + encoding.encoding().name() + "\n");
                        }
                );
            }
            if (totals[0] == 0) {
                throw new OpenToolException("EMPTY_FASTQ", "No FASTQ records were found.", "Check the input.");
            }
        });
        context.progress(1.0, "FASTQ filtering completed");
        return new ToolResult(
                "Retained " + totals[1] + " of " + totals[0] + " FASTQ reads",
                "result.fastq.filter.summary", List.of(totals[1], totals[0]),
                totals[2] + " reads filtered; per-read reasons are in fastq-filter-report.tsv.",
                "result.fastq.filter.detail", List.of(totals[2]),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Input records", Long.toString(totals[0])),
                        List.of("Retained", Long.toString(totals[1])),
                        List.of("Filtered", Long.toString(totals[2]))
                )),
                List.of(new OutputArtifact(output, "Filtered FASTQ",
                                io.opentools.formats.Compression.isGzip(output)
                                        ? "application/gzip" : "text/x-fastq"),
                        new OutputArtifact(report, "FASTQ filter report", "text/tab-separated-values")),
                Map.of("inputRecords", totals[0], "retained", totals[1], "filtered", totals[2],
                        "qualityEncoding", encoding.encoding().name(),
                        "qualityAutodetected", encoding.autodetected(),
                        "threads", threads, "parallelRecords", threads > 1)
        );
    }

    private static FilteredRead filterRead(
            NumberedRead numbered,
            ToolRequest request,
            FastqQualityEncoding encoding
    ) {
        FastqRecord record = numbered.record();
        int end = Math.max(0, record.sequence().length() - request.integer("trimRight"));
        int start = Math.min(end, request.integer("trimLeft"));
        String sequence = record.sequence().substring(start, end);
        String quality = record.quality().substring(start, end);
        double meanQuality = meanQuality(quality, encoding);
        long n = sequence.toUpperCase(Locale.ROOT).chars()
                .filter(value -> value == 'N').count();
        double nPercent = sequence.isEmpty() ? 0 : n * 100.0 / sequence.length();
        String reason = reason(sequence.length(), meanQuality, nPercent, request);
        return new FilteredRead(
                numbered,
                sequence,
                quality,
                meanQuality,
                nPercent,
                reason,
                "PASS".equals(reason)
        );
    }

    private static String reason(int length, double meanQuality, double nPercent, ToolRequest request) {
        if (length < request.integer("minLength")) return "LENGTH_BELOW_MINIMUM";
        if (length > request.integer("maxLength")) return "LENGTH_ABOVE_MAXIMUM";
        if (meanQuality < parse(request.value("minMeanQuality"))) return "MEAN_QUALITY_BELOW_MINIMUM";
        if (nPercent > parse(request.value("maxNPercent"))) return "N_ABOVE_MAXIMUM";
        return "PASS";
    }

    private static double meanQuality(String quality, FastqQualityEncoding encoding) {
        if (quality.isEmpty()) return 0;
        double sum = 0;
        for (int index = 0; index < quality.length(); index++) {
            sum += encoding.score(quality.charAt(index));
        }
        return sum / (double) quality.length();
    }

    private static double parse(Object value) {
        return value == null ? 0 : Double.parseDouble(String.valueOf(value));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String escape(String value) {
        return value.replace("\t", "\\t").replace("\n", "\\n");
    }

    private record NumberedRead(long recordNumber, FastqRecord record) {
    }

    private record FilteredRead(
            NumberedRead read,
            String sequence,
            String quality,
            double meanQuality,
            double nPercent,
            String reason,
            boolean keep
    ) {
    }
}
