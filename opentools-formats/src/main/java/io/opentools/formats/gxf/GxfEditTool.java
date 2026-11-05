package io.opentools.formats.gxf;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.Compression;
import io.opentools.formats.FormatToolSupport;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GxfEditTool extends AbstractOpenTool {
    private static final Pattern GTF_ATTRIBUTE = Pattern.compile("(\\S+)\\s+\"([^\"]*)\"");
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "gxf.edit",
            "Edit GFF3/GTF",
            "Edit source, type, strand, and attributes while preserving annotation headers.",
            "Genome annotation",
            List.of("GFF edit", "GTF edit", "annotation edit", "GFF 编辑", "GTF 编辑"),
            List.of("GFF3", "GTF", "GFF3.gz", "GTF.gz"),
            List.of("GFF3", "GTF"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Input annotation", "GFF3 or GTF, optionally gzip-compressed."),
                    ToolParameter.string("setType", "Set feature type",
                            "Replace column 3 when non-blank.", ""),
                    ToolParameter.string("setSource", "Set source",
                            "Replace column 2 when non-blank.", ""),
                    new ToolParameter("setStrand", "Set strand",
                            "Replace column 7 or keep it unchanged.", ParameterType.ENUM,
                            false, "KEEP", List.of("KEEP", "PLUS", "MINUS", "DOT"),
                            null, null, false, null),
                    ToolParameter.string("addAttribute", "Add attribute",
                            "Optional key=value GFF3 attribute or key/value GTF attribute.", ""),
                    ToolParameter.string("removeAttributes", "Remove attributes",
                            "Comma-separated attribute keys to remove.", ""),
                    ToolParameter.optionalOutputFile("output", "Output annotation",
                            "Leave blank to save in the run directory."),
                    ToolParameter.bool("overwrite", "Overwrite output",
                            "Allow replacing an existing output.", false)
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
        if (!request.string("addAttribute").isBlank()
                && !request.string("addAttribute").contains("=")
                && !request.string("addAttribute").contains(" ")) {
            result = result.merge(ValidationResult.of(
                    io.opentools.api.tool.ValidationIssue.error(
                            "addAttribute",
                            "Attribute must be key=value or key value.",
                            "For example: gene_id=Gene1 or gene_id Gene1."
                    )));
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        Path output = FormatToolSupport.outputPath(request, context, "edited.gff3");
        Set<String> remove = parseKeys(request.string("removeAttributes"));
        long[] counters = new long[3];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(request.path("input")), StandardCharsets.UTF_8))) {
            FormatToolSupport.writeAtomically(output, writer -> {
                String line;
                boolean fasta = false;
                while ((line = reader.readLine()) != null) {
                    context.cancellationToken().throwIfCancelled();
                    if (fasta) {
                        writer.write(line);
                        writer.newLine();
                        continue;
                    }
                    if (line.startsWith("##FASTA")) {
                        fasta = true;
                        writer.write(line);
                        writer.newLine();
                        continue;
                    }
                    if (line.isBlank() || line.startsWith("#")) {
                        writer.write(line);
                        writer.newLine();
                        counters[2]++;
                        continue;
                    }
                    String[] columns = line.split("\t", -1);
                    if (columns.length != 9) {
                        throw new OpenToolException(
                                "GXF_FORMAT_ERROR",
                                "Expected 9 tab-separated columns near line " + (counters[0] + counters[2] + 1) + ".",
                                "Use GFF3 or GTF with nine columns."
                        );
                    }
                    if (!request.string("setSource").isBlank()) {
                        columns[1] = request.string("setSource");
                    }
                    if (!request.string("setType").isBlank()) {
                        columns[2] = request.string("setType");
                    }
                    switch (request.string("setStrand")) {
                        case "PLUS" -> columns[6] = "+";
                        case "MINUS" -> columns[6] = "-";
                        case "DOT" -> columns[6] = ".";
                        default -> { }
                    }
                    columns[8] = editAttributes(columns[8], request.string("addAttribute"), remove);
                    writer.write(String.join("\t", columns));
                    writer.newLine();
                    counters[0]++;
                    counters[1]++;
                }
            });
        }
        if (counters[0] == 0) {
            throw new OpenToolException("EMPTY_GXF", "No annotation records were found.", "Check the input format.");
        }
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("records", counters[0]);
        metrics.put("modified", counters[1]);
        metrics.put("headers", counters[2]);
        context.progress(1.0, "Annotation editing completed");
        return new ToolResult(
                "Edited " + counters[1] + " annotation records",
                List.of(counters[0]),
                counters[2] + " header/comment lines preserved.",
                List.of(counters[2]),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Records", Long.toString(counters[0])),
                        List.of("Modified", Long.toString(counters[1])),
                        List.of("Headers", Long.toString(counters[2]))
                )),
                List.of(new OutputArtifact(output, "Edited annotation", "text/plain")),
                metrics
        );
    }

    private static String editAttributes(String text, String add, Set<String> remove) {
        boolean gtf = text.contains("\"") && !text.contains("=");
        if (gtf) {
            List<String> values = new ArrayList<>();
            Matcher matcher = GTF_ATTRIBUTE.matcher(text);
            while (matcher.find()) {
                if (!remove.contains(matcher.group(1))) {
                    values.add(matcher.group(1) + " \"" + matcher.group(2) + "\";");
                }
            }
            if (!add.isBlank()) {
                values.add(gtfAttribute(add));
            }
            return String.join(" ", values);
        }
        List<String> values = new ArrayList<>();
        if (!text.isBlank() && !text.equals(".")) {
            for (String part : text.split(";")) {
                String trimmed = part.trim();
                int separator = trimmed.indexOf('=');
                String key = separator < 0 ? trimmed : trimmed.substring(0, separator).trim();
                if (!key.isEmpty() && !remove.contains(key)) {
                    values.add(trimmed);
                }
            }
        }
        if (!add.isBlank()) {
            values.add(add.contains("=") ? add.trim() : add.trim().replaceFirst("\\s+", "="));
        }
        return String.join(";", values);
    }

    private static String gtfAttribute(String add) {
        String trimmed = add.trim();
        int separator = trimmed.indexOf('=');
        if (separator < 0) {
            separator = trimmed.indexOf(' ');
        }
        String key = separator < 0 ? trimmed : trimmed.substring(0, separator).trim();
        String value = separator < 0 ? "" : trimmed.substring(separator + 1).trim();
        return key + " \"" + value + "\";";
    }

    private static Set<String> parseKeys(String value) {
        Set<String> keys = new java.util.LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return keys;
        }
        for (String part : value.split(",")) {
            if (!part.isBlank()) {
                keys.add(part.trim());
            }
        }
        return keys;
    }
}
