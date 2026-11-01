package io.opentools.analysis.sequence;

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
import io.opentools.core.util.OrderedParallelProcessor;
import io.opentools.formats.fasta.FastaReader;
import io.opentools.formats.fasta.FastaRecord;
import io.opentools.formats.fasta.FastaWriter;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class FastaFilterTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "fasta.filter",
            "Filter FASTA",
            "Stream FASTA records and filter by IDs, regular expressions, length, GC, or N content.",
            "Sequence",
            List.of("fasta filter", "sequence filter", "GC filter", "FASTA 过滤", "序列过滤"),
            List.of("FASTA", "FASTA.gz"),
            List.of("FASTA", "FASTA.gz", "TSV report"),
            "1.1.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input FASTA", "A FASTA file, optionally gzip-compressed."),
                    ToolParameter.integer("threads", "Threads",
                            "0 uses AUTO for large files; 1 disables parallel record processing.",
                            0, 0, 64),
                    ToolParameter.optionalText("includeIds", "Include IDs",
                            "Optional IDs separated by whitespace, commas, or semicolons. Blank keeps all IDs."),
                    ToolParameter.optionalText("excludeIds", "Exclude IDs",
                            "Optional IDs separated by whitespace, commas, or semicolons."),
                    ToolParameter.string("includeRegex", "Include ID regex",
                            "Optional Java regular expression. A record is kept when the regex is found in its ID token.", ""),
                    ToolParameter.string("excludeRegex", "Exclude ID regex",
                            "Optional Java regular expression. Matching ID tokens are filtered out.", ""),
                    ToolParameter.bool("ignoreCase", "Ignore ID case",
                            "Apply ID and regular-expression matching without case sensitivity.", true),
                    ToolParameter.integer("minLength", "Minimum length",
                            "Minimum sequence length, inclusive.", 0, 0, Integer.MAX_VALUE),
                    ToolParameter.integer("maxLength", "Maximum length",
                            "Maximum sequence length, inclusive.", Integer.MAX_VALUE, 0, Integer.MAX_VALUE),
                    ToolParameter.decimal("minGcPercent", "Minimum GC percent",
                            "Minimum GC percentage. GC is calculated over A/C/G/T/U bases.", 0.0, 0.0, 100.0),
                    ToolParameter.decimal("maxGcPercent", "Maximum GC percent",
                            "Maximum GC percentage. GC is calculated over A/C/G/T/U bases.", 100.0, 0.0, 100.0),
                    ToolParameter.decimal("minNPercent", "Minimum N percent",
                            "Minimum N percentage over all sequence characters.", 0.0, 0.0, 100.0),
                    ToolParameter.decimal("maxNPercent", "Maximum N percent",
                            "Maximum N percentage over all sequence characters.", 100.0, 0.0, 100.0),
                    ToolParameter.optionalOutputFile("output", "Output FASTA",
                            "Leave blank to save inside the run directory. A .gz suffix enables gzip output."),
                    ToolParameter.bool("overwrite", "Overwrite output",
                            "Allow replacing an existing custom output file.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request)
                .merge(SequenceTaskSupport.validateOutput(request, request.path("input")));
        if (request.integer("minLength") > request.integer("maxLength")) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "minLength",
                    "Minimum length cannot exceed maximum length.",
                    "Lower minLength or raise maxLength."
            )));
        }
        if (decimal(request, "minGcPercent") > decimal(request, "maxGcPercent")) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "minGcPercent",
                    "Minimum GC percent cannot exceed maximum GC percent.",
                    "Adjust the GC thresholds."
            )));
        }
        if (decimal(request, "minNPercent") > decimal(request, "maxNPercent")) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "minNPercent",
                    "Minimum N percent cannot exceed maximum N percent.",
                    "Adjust the N-content thresholds."
            )));
        }
        result = validateRegex(result, request, "includeRegex");
        result = validateRegex(result, request, "excludeRegex");
        return result;
    }

    @Override
    public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        Path input = request.path("input");
        Path output = SequenceTaskSupport.outputPath(request, context, "filtered.fa");
        Path report = context.defaultOutputPath("filter-report.tsv");

        Set<String> includeIds = normalizedSet(SequenceTaskSupport.parseIds(request.string("includeIds")),
                request.bool("ignoreCase"));
        Set<String> excludeIds = normalizedSet(SequenceTaskSupport.parseIds(request.string("excludeIds")),
                request.bool("ignoreCase"));
        Pattern includeRegex = compile(request.string("includeRegex"), request.bool("ignoreCase"));
        Pattern excludeRegex = compile(request.string("excludeRegex"), request.bool("ignoreCase"));
        int minLength = request.integer("minLength");
        int maxLength = request.integer("maxLength");
        double minGc = decimal(request, "minGcPercent");
        double maxGc = decimal(request, "maxGcPercent");
        double minN = decimal(request, "minNPercent");
        double maxN = decimal(request, "maxNPercent");
        int threads = OrderedParallelProcessor.resolveThreads(
                request.integer("threads"), Files.size(input), 16L * 1024 * 1024);

        long[] totals = new long[3];
        Map<String, Long> reasonCounts = new LinkedHashMap<>();

        context.log("Filtering FASTA: " + input);
        SequenceTaskSupport.writeAtomically(output, report, (outputWriter, reportWriter) -> {
            long[] localTotals = new long[3];
            Map<String, Long> localReasonCounts = new LinkedHashMap<>();

            writeReportHeader(reportWriter);
            try (FastaReader reader = new FastaReader(input)) {
                long[] sourceIndex = new long[1];
                OrderedParallelProcessor.process(
                        threads,
                        64,
                        context.cancellationToken(),
                        () -> {
                            FastaRecord record = reader.next();
                            return record == null
                                    ? null
                                    : new NumberedRecord(++sourceIndex[0], record);
                        },
                        numbered -> filterRecord(
                                numbered,
                                request.bool("ignoreCase"),
                                includeIds,
                                excludeIds,
                                includeRegex,
                                excludeRegex,
                                minLength,
                                maxLength,
                                minGc,
                                maxGc,
                                minN,
                                maxN
                        ),
                        result -> {
                            localTotals[0]++;
                            if (result.keep()) {
                                FastaWriter.write(outputWriter, result.record(), 80);
                                localTotals[1]++;
                            } else {
                                localTotals[2]++;
                            }
                            localReasonCounts.merge(result.reason(), 1L, Long::sum);
                            writeReportRow(
                                    reportWriter,
                                    result.recordNumber(),
                                    result.record(),
                                    result.keep(),
                                    result.reason(),
                                    result.length(),
                                    result.gcPercent(),
                                    result.nPercent()
                            );
                            if (localTotals[0] % 10_000 == 0) {
                                context.log("Processed " + localTotals[0] + " FASTA records");
                            }
                        }
                );
            }
            if (localTotals[0] == 0) {
                throw new OpenToolException(
                        "EMPTY_FASTA",
                        "No FASTA records were found.",
                        "Check that the input starts with '>' headers."
                );
            }
            totals[0] = localTotals[0];
            totals[1] = localTotals[1];
            totals[2] = localTotals[2];
            reasonCounts.putAll(localReasonCounts);
        });

        long inputRecords = totals[0];
        long retained = totals[1];
        long filtered = totals[2];
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Input records", Long.toString(inputRecords)));
        rows.add(List.of("Retained", Long.toString(retained)));
        rows.add(List.of("Filtered", Long.toString(filtered)));
        reasonCounts.forEach((reason, count) -> rows.add(List.of("Reason: " + reason, Long.toString(count))));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("inputRecords", inputRecords);
        metrics.put("retained", retained);
        metrics.put("filtered", filtered);
        metrics.put("reasonCounts", Map.copyOf(reasonCounts));
        metrics.put("threads", threads);
        metrics.put("parallelRecords", threads > 1);
        context.progress(1.0, "FASTA filter completed");
        return new TaskResult(
                "Retained " + retained + " of " + inputRecords + " FASTA records",
                "result.fasta.filter.summary",
                List.of(retained, inputRecords),
                "Filtered " + filtered + " records; per-record reasons are in filter-report.tsv.",
                "result.fasta.filter.detail",
                List.of(filtered),
                new ResultTable(List.of("Metric", "Value"), rows),
                List.of(
                        new OutputArtifact(output, "Filtered FASTA", mediaType(output)),
                        new OutputArtifact(report, "Filter report", "text/tab-separated-values")
                ),
                metrics
        );
    }

    private static FilterResult filterRecord(
            NumberedRecord numbered,
            boolean ignoreCase,
            Set<String> includeIds,
            Set<String> excludeIds,
            Pattern includeRegex,
            Pattern excludeRegex,
            int minLength,
            int maxLength,
            double minGc,
            double maxGc,
            double minN,
            double maxN
    ) {
        FastaRecord record = numbered.record();
        requireId(record);
        String normalizedId = normalize(record.id(), ignoreCase);
        long length = record.sequence().length();
        BaseCounts counts = countBases(record.sequence());
        double gcPercent = percentage(counts.gc(), counts.canonical());
        double nPercent = percentage(counts.n(), length);
        String reason = filterReason(
                normalizedId,
                length,
                gcPercent,
                nPercent,
                includeIds,
                excludeIds,
                includeRegex,
                excludeRegex,
                minLength,
                maxLength,
                minGc,
                maxGc,
                minN,
                maxN
        );
        return new FilterResult(
                numbered.recordNumber(),
                record,
                "PASS".equals(reason),
                reason,
                length,
                gcPercent,
                nPercent
        );
    }

    private static String filterReason(
            String id,
            long length,
            double gcPercent,
            double nPercent,
            Set<String> includeIds,
            Set<String> excludeIds,
            Pattern includeRegex,
            Pattern excludeRegex,
            int minLength,
            int maxLength,
            double minGc,
            double maxGc,
            double minN,
            double maxN
    ) {
        if (excludeIds.contains(id)) {
            return "ID_EXCLUDED";
        }
        if (!includeIds.isEmpty() && !includeIds.contains(id)) {
            return "ID_NOT_INCLUDED";
        }
        if (includeRegex != null && !includeRegex.matcher(id).find()) {
            return "REGEX_NOT_MATCHED";
        }
        if (excludeRegex != null && excludeRegex.matcher(id).find()) {
            return "REGEX_EXCLUDED";
        }
        if (length < minLength) {
            return "LENGTH_BELOW_MINIMUM";
        }
        if (length > maxLength) {
            return "LENGTH_ABOVE_MAXIMUM";
        }
        if (gcPercent < minGc) {
            return "GC_BELOW_MINIMUM";
        }
        if (gcPercent > maxGc) {
            return "GC_ABOVE_MAXIMUM";
        }
        if (nPercent < minN) {
            return "N_BELOW_MINIMUM";
        }
        if (nPercent > maxN) {
            return "N_ABOVE_MAXIMUM";
        }
        return "PASS";
    }

    private static void writeReportHeader(BufferedWriter writer) throws Exception {
        writer.write("record\tid\tstatus\treason\tlength\tgc_percent\tn_percent\theader");
        writer.newLine();
    }

    private static void writeReportRow(
            BufferedWriter writer,
            long recordNumber,
            FastaRecord record,
            boolean keep,
            String reason,
            long length,
            double gcPercent,
            double nPercent
    ) throws Exception {
        writer.write(Long.toString(recordNumber));
        writer.write('\t');
        writer.write(SequenceTaskSupport.escapeTsv(record.id()));
        writer.write('\t');
        writer.write(keep ? "KEPT" : "FILTERED");
        writer.write('\t');
        writer.write(reason);
        writer.write('\t');
        writer.write(Long.toString(length));
        writer.write('\t');
        writer.write(String.format(Locale.ROOT, "%.2f", gcPercent));
        writer.write('\t');
        writer.write(String.format(Locale.ROOT, "%.2f", nPercent));
        writer.write('\t');
        writer.write(SequenceTaskSupport.escapeTsv(record.header()));
        writer.newLine();
    }

    private static void requireId(FastaRecord record) {
        if (record.id().isBlank()) {
            throw new OpenToolException(
                    "FASTA_EMPTY_ID",
                    "A FASTA record has no identifier.",
                    "Add a non-empty identifier after the '>' character."
            );
        }
    }

    private static BaseCounts countBases(String sequence) {
        long gc = 0;
        long canonical = 0;
        long n = 0;
        for (int index = 0; index < sequence.length(); index++) {
            char value = Character.toUpperCase(sequence.charAt(index));
            if (value == 'G' || value == 'C') {
                gc++;
            }
            if (value == 'A' || value == 'C' || value == 'G'
                    || value == 'T' || value == 'U') {
                canonical++;
            }
            if (value == 'N') {
                n++;
            }
        }
        return new BaseCounts(gc, canonical, n);
    }

    private static double percentage(long numerator, long denominator) {
        return denominator <= 0 ? 0.0 : numerator * 100.0 / denominator;
    }

    private static Set<String> normalizedSet(List<String> values, boolean ignoreCase) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(normalize(value, ignoreCase));
        }
        return result;
    }

    private static String normalize(String value, boolean ignoreCase) {
        return ignoreCase ? value.toLowerCase(Locale.ROOT) : value;
    }

    private static ValidationResult validateRegex(
            ValidationResult result,
            ToolRequest request,
            String field
    ) {
        String value = request.string(field);
        if (value.isBlank()) {
            return result;
        }
        try {
            Pattern.compile(value, request.bool("ignoreCase")
                    ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                    : 0);
            return result;
        } catch (PatternSyntaxException ex) {
            return result.merge(ValidationResult.of(ValidationIssue.error(
                    field,
                    "Invalid regular expression: " + ex.getDescription(),
                    "Correct the regex syntax."
            )));
        }
    }

    private static Pattern compile(String value, boolean ignoreCase) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Pattern.compile(value, ignoreCase
                ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                : 0);
    }

    private static double decimal(ToolRequest request, String field) {
        Object value = request.value(field);
        return value == null || String.valueOf(value).isBlank()
                ? 0.0
                : Double.parseDouble(String.valueOf(value));
    }

    private static String mediaType(Path output) {
        return io.opentools.formats.Compression.isGzip(output)
                ? "application/gzip"
                : "text/x-fasta";
    }

    private record NumberedRecord(long recordNumber, FastaRecord record) {
    }

    private record BaseCounts(long gc, long canonical, long n) {
    }

    private record FilterResult(
            long recordNumber,
            FastaRecord record,
            boolean keep,
            String reason,
            long length,
            double gcPercent,
            double nPercent
    ) {
    }
}
