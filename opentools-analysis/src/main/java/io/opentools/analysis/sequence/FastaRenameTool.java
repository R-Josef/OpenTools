package io.opentools.analysis.sequence;

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
import io.opentools.core.util.OrderedParallelProcessor;
import io.opentools.formats.FormatToolSupport;
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
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class FastaRenameTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "fasta.rename",
            "Rename FASTA Identifiers",
            "Prefix, suffix, map, or regex-rename FASTA ID tokens while detecting duplicate output IDs.",
            "Sequence",
            List.of("fasta rename", "rename fasta", "FASTA 重命名", "序列 ID 映射"),
            List.of("FASTA", "FASTA.gz", "Mapping text"),
            List.of("FASTA", "FASTA.gz", "TSV report"),
            "1.1.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input FASTA", "FASTA, optionally gzip-compressed."),
                    ToolParameter.integer("threads", "Threads",
                            "0 uses AUTO for large files; 1 disables parallel record processing.",
                            0, 0, 64),
                    new ToolParameter("mode", "Rename mode", "Choose the identifier transformation.",
                            ParameterType.ENUM, false, "PREFIX_SUFFIX",
                            List.of("PREFIX_SUFFIX", "MAP", "REGEX"), null, null, false, null),
                    ToolParameter.string("idPrefix", "ID prefix", "Text prepended in PREFIX_SUFFIX mode.", ""),
                    ToolParameter.string("idSuffix", "ID suffix", "Text appended in PREFIX_SUFFIX mode.", ""),
                    ToolParameter.optionalText("mapping", "ID mapping",
                            "MAP mode: one old-to-new pair per line, separated by tab or '='."),
                    ToolParameter.string("searchRegex", "Search regex", "REGEX mode Java regular expression.", ""),
                    ToolParameter.string("replacement", "Replacement", "REGEX mode replacement.", ""),
                    new ToolParameter("duplicatePolicy", "Duplicate IDs", "Handle duplicate output identifiers.",
                            ParameterType.ENUM, false, "APPEND", List.of("ERROR", "APPEND"),
                            null, null, false, null),
                    ToolParameter.optionalOutputFile("output", "Output FASTA",
                            "Leave blank to save in the run directory. A .gz suffix enables gzip."),
                    ToolParameter.bool("overwrite", "Overwrite output", "Allow replacing an existing output.", false)
            )
    );

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ValidationResult validate(ToolRequest request) {
        ValidationResult result = validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("input")));
        if ("MAP".equals(request.string("mode")) && parseMapping(request.string("mapping")).isEmpty()) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "mapping", "No ID mappings were provided.", "Add OLD<TAB>NEW pairs.")));
        }
        if ("REGEX".equals(request.string("mode"))) {
            if (request.string("searchRegex").isEmpty()) {
                result = result.merge(ValidationResult.of(ValidationIssue.error(
                        "searchRegex", "Search regex is required in REGEX mode.", "Enter a Java regex.")));
            } else {
                try {
                    Pattern.compile(request.string("searchRegex"));
                } catch (PatternSyntaxException ex) {
                    result = result.merge(ValidationResult.of(ValidationIssue.error(
                            "searchRegex", "Invalid regex: " + ex.getDescription(), "Correct the regex.")));
                }
            }
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path output = FormatToolSupport.outputPath(request, context, "renamed.fa");
        Path report = context.defaultOutputPath("rename-report.tsv");
        Map<String, String> mapping = "MAP".equals(request.string("mode"))
                ? parseMapping(request.string("mapping")) : Map.of();
        Set<String> usedMappings = new LinkedHashSet<>();
        Pattern regex = "REGEX".equals(request.string("mode"))
                ? Pattern.compile(request.string("searchRegex")) : null;
        int threads = OrderedParallelProcessor.resolveThreads(
                request.integer("threads"), Files.size(request.path("input")), 16L * 1024 * 1024);
        long[] totals = new long[4];

        FormatToolSupport.writePairAtomically(output, report, (writer, reportWriter) -> {
            reportWriter.write("record\told_id\tnew_id\tstatus\tdiagnostic\n");
            Set<String> outputIds = new LinkedHashSet<>();
            Map<String, Integer> duplicateNumbers = new LinkedHashMap<>();
            try (FastaReader reader = new FastaReader(request.path("input"))) {
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
                        numbered -> renameRecord(numbered, request, mapping, regex),
                        candidate -> {
                            totals[0]++;
                            if (candidate.mappingUsed()) {
                                usedMappings.add(candidate.record().id());
                            }
                            String newId = candidate.requestedId();
                            String status = "RENAMED";
                            String diagnostic = "";
                            if (!outputIds.add(newId)) {
                                if ("ERROR".equals(request.string("duplicatePolicy"))) {
                                    throw new OpenToolException("DUPLICATE_OUTPUT_ID",
                                            "Renaming creates duplicate ID " + newId + ".",
                                            "Use APPEND duplicate policy or adjust the mapping.");
                                }
                                int number = duplicateNumbers.merge(
                                        candidate.requestedId(), 2, (left, ignored) -> left + 1);
                                newId = candidate.requestedId() + "_dup" + number;
                                while (!outputIds.add(newId)) {
                                    number = duplicateNumbers.merge(
                                            candidate.requestedId(), 2,
                                            (left, ignored) -> left + 1);
                                    newId = candidate.requestedId() + "_dup" + number;
                                }
                                totals[2]++;
                                status = "DUPLICATE_ADJUSTED";
                                diagnostic = "Duplicate requested ID renamed to " + newId;
                            }
                            FastaRecord record = candidate.record();
                            String newHeader = newId;
                            if (!record.id().equals(record.header())) {
                                String remainder = record.header().substring(record.id().length());
                                newHeader += remainder;
                            }
                            FastaWriter.write(
                                    writer,
                                    new FastaRecord(newId, newHeader, record.sequence()),
                                    80
                            );
                            reportWriter.write(totals[0] + "\t" + escape(record.id()) + "\t"
                                    + escape(newId) + "\t" + status + "\t"
                                    + escape(diagnostic) + "\n");
                        }
                );
            }
            if (totals[0] == 0) {
                throw new OpenToolException("EMPTY_FASTA", "No FASTA records were found.", "Check the input.");
            }
            if ("MAP".equals(request.string("mode"))) {
                for (String unused : mapping.keySet()) {
                    if (!usedMappings.contains(unused)) {
                        reportWriter.write("\t" + escape(unused) + "\t\tMAPPING_UNUSED\tNo input ID matched\n");
                    }
                }
            }
            totals[1] = totals[0];
            totals[3] = mapping.size() - usedMappings.size();
        });
        context.progress(1.0, "FASTA rename completed");
        return new ToolResult(
                "Renamed " + totals[0] + " FASTA records",
                "result.fasta.rename.summary", List.of(totals[0]),
                totals[2] + " duplicate IDs adjusted; " + totals[3] + " mappings unused.",
                "result.fasta.rename.detail", List.of(totals[2], totals[3]),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Records", Long.toString(totals[0])),
                        List.of("Duplicate adjustments", Long.toString(totals[2])),
                        List.of("Unused mappings", Long.toString(totals[3]))
                )),
                List.of(new OutputArtifact(output, "Renamed FASTA", "text/x-fasta"),
                        new OutputArtifact(report, "Rename report", "text/tab-separated-values")),
                Map.of("records", totals[0], "duplicateAdjusted", totals[2],
                        "unusedMappings", totals[3], "threads", threads,
                        "parallelRecords", threads > 1)
        );
    }

    private static RenameCandidate renameRecord(
            NumberedRecord numbered,
            ToolRequest request,
            Map<String, String> mapping,
            Pattern regex
    ) {
        FastaRecord record = numbered.record();
        if (record.id().isBlank()) {
            throw new OpenToolException("FASTA_EMPTY_ID", "A FASTA record has no identifier.",
                    "Add an identifier after '>'.");
        }
        boolean mappingUsed = false;
        String renamed = switch (request.string("mode")) {
            case "MAP" -> {
                String mapped = mapping.get(record.id());
                if (mapped != null) {
                    mappingUsed = true;
                    yield mapped;
                }
                yield record.id();
            }
            case "REGEX" -> regex.matcher(record.id()).replaceAll(request.string("replacement"));
            default -> request.string("idPrefix") + record.id() + request.string("idSuffix");
        };
        return new RenameCandidate(numbered.recordNumber(), record, renamed, mappingUsed);
    }

    private static Map<String, String> parseMapping(String text) {
        Map<String, String> mapping = new LinkedHashMap<>();
        if (text == null) {
            return mapping;
        }
        for (String line : text.lines().map(String::trim).toList()) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\\t", 2);
            if (fields.length != 2) {
                int separator = line.indexOf('=');
                if (separator > 0) {
                    fields = new String[]{line.substring(0, separator), line.substring(separator + 1)};
                }
            }
            if (fields.length == 2 && !fields[0].trim().isEmpty() && !fields[1].trim().isEmpty()) {
                mapping.put(fields[0].trim(), fields[1].trim());
            }
        }
        return mapping;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\t", "\\t").replace("\n", "\\n");
    }

    private record NumberedRecord(long recordNumber, FastaRecord record) {
    }

    private record RenameCandidate(
            long recordNumber,
            FastaRecord record,
            String requestedId,
            boolean mappingUsed
    ) {
    }
}
