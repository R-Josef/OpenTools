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
import io.opentools.core.util.OrderedParallelProcessor;
import io.opentools.formats.FormatToolSupport;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class GxfFilterTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "gxf.filter",
            "Filter GFF3/GTF",
            "Filter annotation records by type, source, sequence, strand, coordinates, length, and attributes.",
            "Genome annotation",
            List.of("gff filter", "gtf filter", "annotation filter", "GFF 过滤", "注释过滤"),
            List.of("GFF3", "GTF", "GFF3.gz", "GTF.gz"),
            List.of("GFF3", "GTF", "GFF3.gz", "GTF.gz"),
            "1.1.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input annotation", "GFF3 or GTF, optionally gzip-compressed."),
                    ToolParameter.integer("threads", "Threads",
                            "0 uses AUTO for large files; 1 disables parallel record processing.",
                            0, 0, 64),
                    ToolParameter.optionalText("types", "Feature types", "Feature types separated by whitespace, commas, or semicolons."),
                    ToolParameter.optionalText("sources", "Sources", "Source values to retain."),
                    ToolParameter.optionalText("seqIds", "Sequence IDs", "Sequence IDs to retain."),
                    new ToolParameter("strand", "Strand", "Required annotation strand.", ParameterType.ENUM,
                            false, "ANY", List.of("ANY", "+", "-", "."), null, null, false, null),
                    ToolParameter.integer("minStart", "Minimum start", "Minimum record start, inclusive.", 0, 0, Integer.MAX_VALUE),
                    ToolParameter.integer("maxEnd", "Maximum end", "Maximum record end, inclusive.", Integer.MAX_VALUE, 0, Integer.MAX_VALUE),
                    ToolParameter.integer("minLength", "Minimum length", "Minimum record length, inclusive.", 0, 0, Integer.MAX_VALUE),
                    ToolParameter.integer("maxLength", "Maximum length", "Maximum record length, inclusive.", Integer.MAX_VALUE, 0, Integer.MAX_VALUE),
                    ToolParameter.string("attributeKey", "Attribute key", "Optional attribute key to test. Blank skips attribute filtering.", ""),
                    ToolParameter.string("attributeRegex", "Attribute regex", "Optional Java regular expression for any value of attributeKey.", ""),
                    ToolParameter.bool("ignoreCase", "Ignore case", "Case-insensitive type/source/sequence matching.", false),
                    ToolParameter.optionalOutputFile("output", "Output annotation", "Leave blank to save in the run directory."),
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
        if (request.integer("minStart") > request.integer("maxEnd")) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "minStart", "Minimum start exceeds maximum end.", "Adjust coordinate limits.")));
        }
        if (request.integer("minLength") > request.integer("maxLength")) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "minLength", "Minimum length exceeds maximum length.", "Adjust length limits.")));
        }
        if (!request.string("attributeRegex").isBlank()) {
            try {
                Pattern.compile(request.string("attributeRegex"), request.bool("ignoreCase")
                        ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
            } catch (PatternSyntaxException ex) {
                result = result.merge(ValidationResult.of(ValidationIssue.error(
                        "attributeRegex", "Invalid regular expression: " + ex.getDescription(),
                        "Correct the regex syntax.")));
            }
            if (request.string("attributeKey").isBlank()) {
                result = result.merge(ValidationResult.of(ValidationIssue.error(
                        "attributeKey", "Attribute key is required when attributeRegex is set.",
                        "Enter the attribute to inspect.")));
            }
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path input = request.path("input");
        Path output = FormatToolSupport.outputPath(request, context, "filtered.gff3");
        boolean ignoreCase = request.bool("ignoreCase");
        Set<String> types = AnnotationTaskSupport.tokens(request.string("types"), ignoreCase);
        Set<String> sources = AnnotationTaskSupport.tokens(request.string("sources"), ignoreCase);
        Set<String> seqIds = AnnotationTaskSupport.tokens(request.string("seqIds"), ignoreCase);
        Pattern attribute = request.string("attributeRegex").isBlank() ? null
                : Pattern.compile(request.string("attributeRegex"), ignoreCase
                ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
        int threads = OrderedParallelProcessor.resolveThreads(
                request.integer("threads"), Files.size(input));
        long[] totals = new long[2];
        Map<String, Long> reasons = new LinkedHashMap<>();

        FormatToolSupport.writeAtomically(output, writer -> {
            long[] localTotals = new long[2];
            Map<String, Long> reasonCounts = new LinkedHashMap<>();
            try (GxfReader reader = new GxfReader(input)) {
                OrderedParallelProcessor.process(
                        threads,
                        512,
                        context.cancellationToken(),
                        reader::next,
                        record -> {
                            String reason = reason(
                                    record,
                                    types,
                                    sources,
                                    seqIds,
                                    request,
                                    attribute,
                                    ignoreCase
                            );
                            return new FilterResult(record, reason);
                        },
                        result -> {
                            localTotals[0]++;
                            if ("PASS".equals(result.reason())) {
                                GxfWriter.write(writer, result.record());
                                localTotals[1]++;
                            }
                            reasonCounts.merge(result.reason(), 1L, Long::sum);
                        }
                );
            }
            if (localTotals[0] == 0) {
                throw new OpenToolException("EMPTY_ANNOTATION", "No GFF3/GTF records were found.",
                        "Check the input format and header lines.");
            }
            totals[0] = localTotals[0];
            totals[1] = localTotals[1];
            reasons.putAll(reasonCounts);
        });
        long inputCount = totals[0];
        long kept = totals[1];
        context.progress(1.0, "Annotation filter completed");
        return new ToolResult(
                "Retained " + kept + " of " + inputCount + " annotation records", List.of(kept, inputCount),
                "Filtered " + (inputCount - kept) + " records.", List.of(inputCount - kept),
                new ResultTable(List.of("Reason", "Count"), reasons.entrySet().stream()
                        .map(entry -> List.of(entry.getKey(), Long.toString(entry.getValue()))).toList()),
                List.of(new OutputArtifact(output, "Filtered annotation", "text/plain")),
                Map.of("inputRecords", inputCount, "retained", kept,
                        "filtered", inputCount - kept, "reasonCounts", Map.copyOf(reasons),
                        "threads", threads, "parallelRecords", threads > 1)
        );
    }

    private record FilterResult(GxfRecord record, String reason) {
    }

    private static String reason(
            GxfRecord record,
            Set<String> types,
            Set<String> sources,
            Set<String> seqIds,
            ToolRequest request,
            Pattern attribute,
            boolean ignoreCase
    ) {
        if (types.isEmpty() == false && !AnnotationTaskSupport.matches(types, record.type(), ignoreCase)) {
            return "TYPE";
        }
        if (sources.isEmpty() == false && !AnnotationTaskSupport.matches(sources, record.source(), ignoreCase)) {
            return "SOURCE";
        }
        if (seqIds.isEmpty() == false && !AnnotationTaskSupport.matches(seqIds, record.seqId(), ignoreCase)) {
            return "SEQUENCE_ID";
        }
        String strand = request.string("strand");
        if (!"ANY".equals(strand) && !String.valueOf(record.strand()).equals(strand)) {
            return "STRAND";
        }
        if (record.start() < request.integer("minStart") || record.end() > request.integer("maxEnd")) {
            return "COORDINATE";
        }
        if (record.length() < request.integer("minLength") || record.length() > request.integer("maxLength")) {
            return "LENGTH";
        }
        if (attribute != null) {
            String key = request.string("attributeKey");
            boolean found = AnnotationTaskSupport.values(record, key).stream()
                    .anyMatch(value -> attribute.matcher(value).find());
            if (!found) {
                return "ATTRIBUTE";
            }
        }
        return "PASS";
    }
}
