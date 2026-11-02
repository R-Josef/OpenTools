package io.opentools.analysis.expression;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExpressionDifferentialInputTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "expression.differential-input",
            "Prepare Differential Expression Input",
            "Combine a count matrix and sample groups into a tidy long table for differential analysis.",
            "Expression",
            List.of("differential input", "DESeq input", "差异分析输入"),
            List.of("Count matrix", "Sample groups"),
            List.of("TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Count matrix", "Genes in the first column and samples in the header."),
                    ToolParameter.requiredFile("groups", "Sample groups", "Two-column sample/group file."),
                    new ToolParameter("delimiter", "Delimiter", "Matrix delimiter.", io.opentools.api.tool.ParameterType.ENUM,
                            false, "AUTO", List.of("AUTO", "TAB", "COMMA", "SEMICOLON"), null, null, false, null),
                    ToolParameter.bool("log2", "Log2 transform counts", "Write log2(x+1) values.", false),
                    ToolParameter.optionalOutputFile("output", "Output long table", "Leave blank to save in the run directory."),
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
        ExpressionMatrix matrix = new ExpressionMatrixParser().parse(
                request.path("input"), request.string("delimiter"), "ERROR", false);
        Map<String, String> groups = readGroups(request.path("groups"));
        for (String sample : matrix.columnNames()) {
            if (!groups.containsKey(sample)) {
                throw new OpenToolException("SAMPLE_GROUP_MISSING",
                        "No group was provided for sample " + sample + ".",
                        "Add a sample/group row for every matrix sample.");
            }
        }
        Path output = FormatToolSupport.outputPath(request, context, "differential-input.tsv");
        FormatToolSupport.writeAtomically(output, writer -> {
            writer.write("gene\tsample\tgroup\tvalue\n");
            for (int row = 0; row < matrix.rowNames().size(); row++) {
                for (int column = 0; column < matrix.columnNames().size(); column++) {
                    double value = matrix.value(row, column);
                    if (request.bool("log2")) {
                        value = Math.log(value + 1) / Math.log(2);
                    }
                    writer.write(matrix.rowNames().get(row));
                    writer.write('\t');
                    writer.write(matrix.columnNames().get(column));
                    writer.write('\t');
                    writer.write(groups.get(matrix.columnNames().get(column)));
                    writer.write('\t');
                    writer.write(String.format(Locale.ROOT, "%.10g", value));
                    writer.newLine();
                }
            }
        });
        context.progress(1.0, "Differential input preparation completed");
        long values = (long) matrix.rowNames().size() * matrix.columnNames().size();
        return new ToolResult(
                "Prepared " + values + " expression observations",
                "result.expression.differential.summary", List.of(values),
                matrix.rowNames().size() + " genes across " + groups.values().stream().distinct().count() + " groups.",
                "result.expression.differential.detail", List.of(matrix.rowNames().size()),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Genes", Integer.toString(matrix.rowNames().size())),
                        List.of("Samples", Integer.toString(matrix.columnNames().size())),
                        List.of("Observations", Long.toString(values))
                )),
                List.of(new OutputArtifact(output, "Differential analysis input", "text/tab-separated-values")),
                Map.of("genes", matrix.rowNames().size(), "samples", matrix.columnNames().size(), "observations", values)
        );
    }

    private static Map<String, String> readGroups(Path path) throws Exception {
        Map<String, String> groups = new LinkedHashMap<>();
        try (var reader = java.nio.file.Files.newBufferedReader(path, java.nio.charset.StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.trim().split("[\\t,;]+");
                if (fields.length >= 2 && !fields[0].equalsIgnoreCase("sample")) {
                    groups.put(fields[0], fields[1]);
                }
            }
        }
        return groups;
    }
}
