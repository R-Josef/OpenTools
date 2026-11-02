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
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.FormatToolSupport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GxfCheckRepairTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "gxf.check-repair",
            "Check and Repair Annotation",
            "Diagnose duplicate IDs, missing parents, unknown parents, missing IDs, and CDS phase; optionally apply safe repairs.",
            "Genome annotation",
            List.of("gff check", "gtf repair", "annotation validation", "注释检查修复"),
            List.of("GFF3", "GTF", "GFF3.gz", "GTF.gz"),
            List.of("GFF3", "GTF", "TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input annotation", "GFF3 or GTF."),
                    ToolParameter.bool("repair", "Apply safe repairs",
                            "Assign missing IDs, suffix duplicate IDs, and normalize invalid CDS phase.", false),
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
        return validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("input")));
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path input = request.path("input");
        Path output = FormatToolSupport.outputPath(request, context, "checked.gff3");
        Path report = context.defaultOutputPath("annotation-diagnostics.tsv");
        List<GxfRecord> records = new ArrayList<>();
        Map<String, Integer> idCounts = new LinkedHashMap<>();
        try (GxfReader reader = new GxfReader(input)) {
            GxfRecord record;
            while ((record = reader.next()) != null) {
                records.add(record);
                String id = record.firstAttribute("ID", "transcript_id", "gene_id");
                if (id != null) {
                    idCounts.merge(id, 1, Integer::sum);
                }
            }
        }
        if (records.isEmpty()) {
            throw new OpenToolException("EMPTY_ANNOTATION", "No annotation records were found.", "Check the input.");
        }
        Set<String> allIds = new HashSet<>(idCounts.keySet());
        Map<String, Integer> duplicateOrdinal = new HashMap<>();
        long[] totals = new long[3];

        FormatToolSupport.writePairAtomically(output, report, (writer, reportWriter) -> {
            reportWriter.write("line\tseverity\tcode\tfeature_id\tmessage\n");
            long errors = 0;
            long warnings = 0;
            long repaired = 0;
            long autoId = 0;
            for (GxfRecord record : records) {
                context.cancellationToken().throwIfCancelled();
                String id = record.firstAttribute("ID", "transcript_id", "gene_id");
                Map<String, List<String>> attributes = new LinkedHashMap<>(record.attributes());
                boolean changed = false;
                if (id == null || id.isBlank()) {
                    autoId++;
                    id = "OpenTools_auto_" + autoId;
                    attributes.put("ID", List.of(id));
                    diagnose(reportWriter, record, "ERROR", "MISSING_ID", id,
                            "Record has no ID; safe suggestion: assign " + id + ".");
                    errors++;
                    if (request.bool("repair")) {
                        changed = true;
                        repaired++;
                    }
                } else if (idCounts.getOrDefault(id, 0) > 1) {
                    int ordinal = duplicateOrdinal.merge(id, 1, Integer::sum);
                    String replacement = id + "_duplicate" + ordinal;
                    diagnose(reportWriter, record, "WARNING", "DUPLICATE_ID", id,
                            "Duplicate ID; safe suggestion: rename this record to " + replacement + ".");
                    warnings++;
                    if (request.bool("repair") && ordinal > 1) {
                        attributes.put("ID", List.of(replacement));
                        id = replacement;
                        changed = true;
                        repaired++;
                    }
                }
                for (String parent : record.attributes().getOrDefault("Parent", List.of())) {
                    if (!allIds.contains(parent)) {
                        diagnose(reportWriter, record, "ERROR", "UNKNOWN_PARENT", id,
                                "Parent " + parent + " is absent; add or correct the parent record.");
                        errors++;
                    }
                }
                if ("CDS".equalsIgnoreCase(record.type())
                        && !List.of("0", "1", "2").contains(record.phase())) {
                    diagnose(reportWriter, record, "WARNING", "INVALID_CDS_PHASE", id,
                            "CDS phase is not 0, 1, or 2; safe suggestion: inspect frame and set phase.");
                    warnings++;
                    if (request.bool("repair")) {
                        changed = true;
                        repaired++;
                    }
                }
                String phase = request.bool("repair") && "CDS".equalsIgnoreCase(record.type())
                        && !List.of("0", "1", "2").contains(record.phase()) ? "0" : record.phase();
                GxfWriter.write(writer, new GxfRecord(record.seqId(), record.source(), record.type(),
                        record.start(), record.end(), record.score(), record.strand(), phase,
                        attributes, rewrite(record.rawAttributes(), attributes),
                        record.lineNumber()));
            }
            totals[0] = errors;
            totals[1] = warnings;
            totals[2] = repaired;
        });
        context.progress(1.0, "Annotation check completed");
        return new ToolResult(
                "Found " + totals[0] + " annotation errors", List.of(totals[0]),
                totals[1] + " warnings; " + totals[2] + " safe repairs applied.", List.of(totals[1], totals[2]),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Errors", Long.toString(totals[0])),
                        List.of("Warnings", Long.toString(totals[1])),
                        List.of("Repairs", Long.toString(totals[2]))
                )),
                List.of(
                        new OutputArtifact(output, "Checked annotation", "text/plain"),
                        new OutputArtifact(report, "Annotation diagnostics", "text/tab-separated-values")
                ),
                Map.of("errors", totals[0], "warnings", totals[1], "repairs", totals[2])
        );
    }

    private static void diagnose(
            java.io.BufferedWriter writer,
            GxfRecord record,
            String severity,
            String code,
            String id,
            String message
    ) throws Exception {
        writer.write(Long.toString(record.lineNumber()));
        writer.write('\t');
        writer.write(severity);
        writer.write('\t');
        writer.write(code);
        writer.write('\t');
        writer.write(escape(id));
        writer.write('\t');
        writer.write(escape(message));
        writer.newLine();
    }

    private static String rewrite(String raw, Map<String, List<String>> attributes) {
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

    private static String escape(String value) {
        return value == null ? "" : value.replace("\t", "\\t").replace("\n", "\\n");
    }
}
