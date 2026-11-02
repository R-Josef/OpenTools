package io.opentools.analysis.sequence;

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
import io.opentools.formats.Compression;
import io.opentools.formats.fasta.FastaReader;
import io.opentools.formats.fasta.FastaRecord;
import io.opentools.formats.fasta.FastaWriter;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FastaSubsequenceTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "fasta.subsequence",
            "Extract FASTA Subsequences",
            "Extract whole records or 1-based inclusive regions by ID, coordinate, and strand.",
            "Sequence",
            List.of("fasta subsequence", "subsequence", "region extract", "FASTA 子序列", "区域提取"),
            List.of("FASTA", "FASTA.gz", "Region table"),
            List.of("FASTA", "FASTA.gz", "TSV diagnostics"),
            "1.1.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input FASTA", "A FASTA file, optionally gzip-compressed."),
                    ToolParameter.string("regions", "Inline regions",
                            "One request per line: ID, or ID<TAB>START<TAB>END[<TAB>STRAND][<TAB>OUTPUT_ID].",
                            ""),
                    ToolParameter.optionalFile("regionsFile", "Region file",
                            "Optional plain or gzip-compressed file with the same tab-separated format as inline regions."),
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
                .merge(SequenceToolSupport.validateOutput(request, request.path("input")));
        if (request.path("regionsFile") != null && !request.string("regions").isBlank()) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "regionsFile",
                    "Use either inline regions or a region file, not both.",
                    "Clear one region source."
            )));
        }
        ParseResult parsed;
        try {
            parsed = parse(regionText(request));
        } catch (Exception ex) {
            return result.merge(ValidationResult.of(ValidationIssue.error(
                    "regionsFile", ex.getMessage(), "Provide a readable UTF-8 region file."
            )));
        }
        for (String error : parsed.errors()) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "regions",
                    error,
                    "Use ID or ID<TAB>START<TAB>END<TAB>STRAND<TAB>OUTPUT_ID."
            )));
        }
        if (parsed.requests().isEmpty() && parsed.errors().isEmpty()) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "regions",
                    "No region requests were provided.",
                    "Add at least one FASTA ID."
            )));
        }
        Set<String> outputIds = new LinkedHashSet<>();
        for (RegionRequest region : parsed.requests()) {
            if (!outputIds.add(region.outputId())) {
                result = result.merge(ValidationResult.of(ValidationIssue.error(
                        "regions",
                        "Duplicate output ID: " + region.outputId(),
                        "Supply a unique output ID in the fifth column."
                )));
            }
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path input = request.path("input");
        Path output = SequenceToolSupport.outputPath(request, context, "subsequences.fa");
        Path diagnostics = context.defaultOutputPath("subsequence-diagnostics.tsv");
        ParseResult parsed = parse(regionText(request));
        if (!parsed.errors().isEmpty()) {
            throw new OpenToolException(
                    "INVALID_REGIONS",
                    parsed.errors().getFirst(),
                    "Correct the region table and validate again."
            );
        }
        List<RegionRequest> regions = parsed.requests();
        Set<String> targetIds = new LinkedHashSet<>();
        regions.forEach(region -> targetIds.add(region.sourceId()));

        context.log("Scanning " + input + " for " + targetIds.size() + " requested IDs");
        Map<String, Long> sourceOccurrences = findRequestedOccurrences(input, targetIds, context);

        long[] totals = new long[4];
        context.log("Extracting " + regions.size() + " requested subsequences");
        SequenceToolSupport.writeAtomically(output, diagnostics, (outputWriter, diagnosticsWriter) -> {
            long extracted = 0;
            long diagnosticCount = 0;
            long duplicateSourceIds = 0;
            long inputRecords = 0;
            Map<String, Long> seen = new HashMap<>();
            writeDiagnosticsHeader(diagnosticsWriter);

            try (FastaReader reader = new FastaReader(input)) {
                FastaRecord record;
                while ((record = reader.next()) != null) {
                    context.cancellationToken().throwIfCancelled();
                    inputRecords++;
                    if (!targetIds.contains(record.id())) {
                        continue;
                    }
                    long occurrence = seen.merge(record.id(), 1L, Long::sum);
                    if (occurrence > 1) {
                        continue;
                    }
                    if (sourceOccurrences.getOrDefault(record.id(), 0L) > 1) {
                        duplicateSourceIds++;
                        writeDiagnostic(
                                diagnosticsWriter,
                                null,
                                "WARNING",
                                "DUPLICATE_SOURCE_ID",
                                record.id(),
                                null,
                                null,
                                null,
                                null,
                                "Source ID occurs " + sourceOccurrences.get(record.id())
                                        + " times; the first record was used."
                        );
                        diagnosticCount++;
                    }

                    for (RegionRequest region : regions) {
                        if (!region.sourceId().equals(record.id())) {
                            continue;
                        }
                        int start = region.whole() ? 1 : region.start();
                        int end = region.whole() ? record.sequence().length() : region.end();
                        if (start > record.sequence().length() || end > record.sequence().length()) {
                            writeDiagnostic(
                                    diagnosticsWriter,
                                    region,
                                    "ERROR",
                                    "OUT_OF_RANGE",
                                    record.id(),
                                    start,
                                    end,
                                    region.strand(),
                                    region.outputId(),
                                    "Requested " + start + "-" + end + " but sequence length is "
                                            + record.sequence().length() + "."
                            );
                            diagnosticCount++;
                            continue;
                        }
                        String subsequence = record.sequence().substring(start - 1, end);
                        if ("-".equals(region.strand())) {
                            subsequence = SequenceAlphabet.reverseComplement(subsequence);
                        }
                        String header = outputHeader(record, region, start, end);
                        FastaWriter.write(
                                outputWriter,
                                new FastaRecord(region.outputId(), header, subsequence),
                                80
                        );
                        extracted++;
                    }
                    if (inputRecords % 10_000 == 0) {
                        context.log("Scanned " + inputRecords + " FASTA records");
                    }
                }
            }

            if (inputRecords == 0) {
                throw new OpenToolException(
                        "EMPTY_FASTA",
                        "No FASTA records were found.",
                        "Check that the input starts with '>' headers."
                );
            }

            for (RegionRequest region : regions) {
                if (sourceOccurrences.getOrDefault(region.sourceId(), 0L) == 0) {
                    writeDiagnostic(
                            diagnosticsWriter,
                            region,
                            "ERROR",
                            "MISSING_ID",
                            region.sourceId(),
                            region.whole() ? null : region.start(),
                            region.whole() ? null : region.end(),
                            region.strand(),
                            region.outputId(),
                            "No FASTA record with this ID was found."
                    );
                    diagnosticCount++;
                }
            }
            totals[0] = regions.size();
            totals[1] = extracted;
            totals[2] = diagnosticCount;
            totals[3] = duplicateSourceIds;
            context.log("Requested " + totals[0] + ", extracted " + extracted + ", diagnostics " + diagnosticCount);
        });

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("requested", totals[0]);
        metrics.put("extracted", totals[1]);
        metrics.put("diagnostics", totals[2]);
        metrics.put("duplicateSourceIds", totals[3]);
        metrics.put("regionSource", request.path("regionsFile") == null ? "inline" : "file");
        ResultTable table = new ResultTable(
                List.of("Metric", "Value"),
                List.of(
                        List.of("Requested regions", Long.toString(totals[0])),
                        List.of("Extracted", Long.toString(totals[1])),
                        List.of("Diagnostics", Long.toString(totals[2])),
                        List.of("Duplicate source IDs", Long.toString(totals[3]))
                )
        );
        context.progress(1.0, "FASTA subsequence extraction completed");
        return new ToolResult(
                "Extracted " + totals[1] + " of " + totals[0] + " requested subsequences",
                "result.fasta.subsequence.summary",
                List.of(totals[1], totals[0]),
                totals[2] + " diagnostics were written to subsequence-diagnostics.tsv.",
                "result.fasta.subsequence.detail",
                List.of(totals[2]),
                table,
                List.of(
                        new OutputArtifact(output, "Subsequence FASTA", mediaType(output)),
                        new OutputArtifact(diagnostics, "Subsequence diagnostics", "text/tab-separated-values")
                ),
                metrics
        );
    }

    private static Map<String, Long> findRequestedOccurrences(
            Path input,
            Set<String> requestedIds,
            ToolExecutionContext context
    ) throws Exception {
        Map<String, Long> occurrenceCounts = new HashMap<>();
        try (FastaReader reader = new FastaReader(input)) {
            FastaRecord record;
            while ((record = reader.next()) != null) {
                context.cancellationToken().throwIfCancelled();
                if (requestedIds.contains(record.id())) {
                    occurrenceCounts.merge(record.id(), 1L, Long::sum);
                }
            }
        }
        return occurrenceCounts;
    }

    private static String outputHeader(FastaRecord record, RegionRequest region, int start, int end) {
        if (region.whole() && "+".equals(region.strand())
                && region.outputId().equals(record.id())) {
            return record.header();
        }
        return region.outputId() + " source=" + record.id()
                + " coordinates=" + start + "-" + end
                + " strand=" + region.strand();
    }

    private static void writeDiagnosticsHeader(BufferedWriter writer) throws Exception {
        writer.write("request_line\tseverity\tcode\tsource_id\tstart\tend\tstrand\toutput_id\tmessage");
        writer.newLine();
    }

    private static String regionText(ToolRequest request) throws Exception {
        Path file = request.path("regionsFile");
        if (file == null) {
            return request.string("regions");
        }
        try (var input = Compression.open(file)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void writeDiagnostic(
            BufferedWriter writer,
            RegionRequest region,
            String severity,
            String code,
            String sourceId,
            Integer start,
            Integer end,
            String strand,
            String outputId,
            String message
    ) throws Exception {
        writer.write(region == null ? "" : Integer.toString(region.lineNumber()));
        writer.write('\t');
        writer.write(severity);
        writer.write('\t');
        writer.write(code);
        writer.write('\t');
        writer.write(SequenceToolSupport.escapeTsv(sourceId));
        writer.write('\t');
        writer.write(start == null ? "" : Integer.toString(start));
        writer.write('\t');
        writer.write(end == null ? "" : Integer.toString(end));
        writer.write('\t');
        writer.write(strand == null ? "" : strand);
        writer.write('\t');
        writer.write(SequenceToolSupport.escapeTsv(outputId));
        writer.write('\t');
        writer.write(SequenceToolSupport.escapeTsv(message));
        writer.newLine();
    }

    private static ParseResult parse(String text) {
        List<RegionRequest> requests = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> outputIds = new LinkedHashSet<>();
        String[] lines = text == null ? new String[0] : text.lines().toArray(String[]::new);
        for (int index = 0; index < lines.length; index++) {
            String raw = lines[index];
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] fields = raw.split("\\t", -1);
            if (fields.length != 1 && (fields.length < 3 || fields.length > 5)) {
                errors.add("Line " + (index + 1)
                        + ": use one bare ID or 3 to 5 tab-separated columns.");
                continue;
            }
            String sourceId = fields[0].trim();
            if (sourceId.isEmpty() || containsWhitespace(sourceId)) {
                errors.add("Line " + (index + 1) + ": source ID must be a non-empty token.");
                continue;
            }

            boolean whole = fields.length == 1;
            int start = 1;
            int end = 0;
            String strand = "+";
            if (fields.length >= 3) {
                String startText = fields[1].trim();
                String endText = fields[2].trim();
                if (startText.isEmpty() && endText.isEmpty()) {
                    whole = true;
                } else {
                    try {
                        start = Integer.parseInt(startText);
                        end = Integer.parseInt(endText);
                    } catch (NumberFormatException ex) {
                        errors.add("Line " + (index + 1) + ": START and END must be integers.");
                        continue;
                    }
                    if (start < 1 || end < 1) {
                        errors.add("Line " + (index + 1) + ": coordinates must be at least 1.");
                        continue;
                    }
                    if (start > end) {
                        errors.add("Line " + (index + 1) + ": START must not exceed END.");
                        continue;
                    }
                }
            }
            if (fields.length >= 4 && !fields[3].trim().isEmpty()) {
                strand = fields[3].trim();
            }
            if (!"+".equals(strand) && !"-".equals(strand)) {
                errors.add("Line " + (index + 1) + ": strand must be + or -.");
                continue;
            }
            String defaultOutput = whole
                    ? (("+".equals(strand)) ? sourceId : sourceId + ":-")
                    : sourceId + ":" + start + "-" + end + ":" + strand;
            String outputId = fields.length == 5 ? fields[4].trim() : defaultOutput;
            if (outputId.isEmpty() || containsWhitespace(outputId)) {
                errors.add("Line " + (index + 1) + ": output ID must be a non-empty token.");
                continue;
            }
            if (!outputIds.add(outputId)) {
                errors.add("Line " + (index + 1) + ": duplicate output ID " + outputId + ".");
                continue;
            }
            requests.add(new RegionRequest(sourceId, start, end, strand, outputId, whole, index + 1));
        }
        return new ParseResult(List.copyOf(requests), List.copyOf(errors));
    }

    private static boolean containsWhitespace(String value) {
        return value.chars().anyMatch(Character::isWhitespace);
    }

    private static String mediaType(Path output) {
        return io.opentools.formats.Compression.isGzip(output)
                ? "application/gzip"
                : "text/x-fasta";
    }

    private record ParseResult(List<RegionRequest> requests, List<String> errors) {
    }

    private record RegionRequest(
            String sourceId,
            int start,
            int end,
            String strand,
            String outputId,
            boolean whole,
            int lineNumber
    ) {
    }
}
