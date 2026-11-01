package io.opentools.formats.gxf;

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
import io.opentools.formats.FormatTaskSupport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GxfIdMapTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "gxf.id-map",
            "Map Annotation IDs",
            "Rename selected GFF3/GTF attributes using an old-to-new mapping table.",
            "Genome annotation",
            List.of("gff id map", "gtf rename id", "annotation id mapping", "注释 ID 映射"),
            List.of("GFF3", "GTF", "Mapping text"),
            List.of("GFF3", "GTF", "TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input annotation", "GFF3 or GTF."),
                    ToolParameter.requiredText("mapping", "ID mapping",
                            "One old-to-new mapping per line, separated by a tab or '='. Lines starting with # are ignored."),
                    ToolParameter.string("attributes", "Attributes to update",
                            "Comma/whitespace-separated attribute keys.", "ID,Parent,gene_id,transcript_id"),
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
                .merge(FormatTaskSupport.validateOutput(request, request.path("input")));
        ParseResult parsed = parse(request.string("mapping"));
        for (String error : parsed.errors()) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "mapping", error, "Use OLD<TAB>NEW or OLD=NEW, one pair per line.")));
        }
        if (parsed.mapping().isEmpty() && parsed.errors().isEmpty()) {
            result = result.merge(ValidationResult.of(ValidationIssue.error(
                    "mapping", "No ID mappings were provided.", "Add at least one mapping.")));
        }
        return result;
    }

    @Override
    public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        Path input = request.path("input");
        Path output = FormatTaskSupport.outputPath(request, context, "mapped.gff3");
        Path report = context.defaultOutputPath("id-mapping-report.tsv");
        ParseResult parsed = parse(request.string("mapping"));
        Map<String, String> mapping = parsed.mapping();
        Set<String> attributes = AnnotationTaskSupport.tokens(request.string("attributes"), false);
        Map<String, Long> occurrences = new LinkedHashMap<>();
        long[] totals = new long[2];

        FormatTaskSupport.writePairAtomically(output, report, (writer, reportWriter) -> {
            reportWriter.write("old_id\tnew_id\toccurrences\n");
            long records = 0;
            long replacements = 0;
            try (GxfReader reader = new GxfReader(input)) {
                GxfRecord record;
                while ((record = reader.next()) != null) {
                    context.cancellationToken().throwIfCancelled();
                    Map<String, List<String>> rewritten = new LinkedHashMap<>();
                    for (Map.Entry<String, List<String>> entry : record.attributes().entrySet()) {
                        List<String> values = new ArrayList<>();
                        for (String value : entry.getValue()) {
                            String replacement = attributes.contains(entry.getKey()) ? mapping.get(value) : null;
                            if (replacement != null) {
                                values.add(replacement);
                                occurrences.merge(value, 1L, Long::sum);
                                replacements++;
                            } else {
                                values.add(value);
                            }
                        }
                        rewritten.put(entry.getKey(), values);
                    }
                    String raw = rewriteRawAttributes(record.rawAttributes(), rewritten);
                    GxfWriter.write(writer, new GxfRecord(record.seqId(), record.source(), record.type(),
                            record.start(), record.end(), record.score(), record.strand(), record.phase(),
                            rewritten, raw, record.lineNumber()));
                    records++;
                }
            }
            if (records == 0) {
                throw new OpenToolException("EMPTY_ANNOTATION", "No annotation records were found.", "Check the input.");
            }
            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                reportWriter.write(escape(entry.getKey()));
                reportWriter.write('\t');
                reportWriter.write(escape(entry.getValue()));
                reportWriter.write('\t');
                reportWriter.write(Long.toString(occurrences.getOrDefault(entry.getKey(), 0L)));
                reportWriter.newLine();
            }
            totals[0] = replacements;
            totals[1] = mapping.size() - occurrences.size();
        });
        long unused = totals[1];
        context.progress(1.0, "Annotation ID mapping completed");
        return new TaskResult(
                "Applied " + totals[0] + " ID replacements",
                "result.gxf.id_map.summary", List.of(totals[0]),
                unused + " mapping keys were unused.",
                "result.gxf.id_map.detail", List.of(unused),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Mappings", Integer.toString(mapping.size())),
                        List.of("Replacements", Long.toString(totals[0])),
                        List.of("Unused mappings", Long.toString(unused))
                )),
                List.of(
                        new OutputArtifact(output, "Mapped annotation", "text/plain"),
                        new OutputArtifact(report, "ID mapping report", "text/tab-separated-values")
                ),
                Map.of("mappings", mapping.size(), "replacements", totals[0], "unused", unused)
        );
    }

    private static String rewriteRawAttributes(String raw, Map<String, List<String>> attributes) {
        if (raw == null || raw.isBlank() || raw.equals(".")) {
            return ".";
        }
        StringBuilder result = new StringBuilder();
        if (raw.contains("=")) {
            for (Map.Entry<String, List<String>> entry : attributes.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }
                if (!result.isEmpty()) {
                    result.append(';');
                }
                result.append(entry.getKey()).append('=').append(String.join(",", entry.getValue()));
            }
        } else {
            for (Map.Entry<String, List<String>> entry : attributes.entrySet()) {
                for (String value : entry.getValue()) {
                    if (!result.isEmpty()) {
                        result.append("; ");
                    }
                    result.append(entry.getKey()).append(" \"").append(value.replace("\"", "\\\"")).append('"');
                }
            }
        }
        return result.isEmpty() ? "." : result.toString();
    }

    private static ParseResult parse(String text) {
        Map<String, String> mapping = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        Set<String> newIds = new LinkedHashSet<>();
        String[] lines = text == null ? new String[0] : text.lines().toArray(String[]::new);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
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
            if (fields.length != 2 || fields[0].trim().isEmpty() || fields[1].trim().isEmpty()) {
                errors.add("Line " + (index + 1) + " is not an old/new pair.");
                continue;
            }
            String oldId = fields[0].trim();
            String newId = fields[1].trim();
            if (mapping.containsKey(oldId)) {
                errors.add("Line " + (index + 1) + " duplicates old ID " + oldId + ".");
                continue;
            }
            if (!newIds.add(newId)) {
                errors.add("Line " + (index + 1) + " reuses new ID " + newId + ".");
                continue;
            }
            mapping.put(oldId, newId);
        }
        return new ParseResult(Map.copyOf(mapping), List.copyOf(errors));
    }

    private static String escape(String value) {
        return value.replace("\t", "\\t").replace("\n", "\\n");
    }

    private record ParseResult(Map<String, String> mapping, List<String> errors) {
    }
}
