package io.opentools.analysis.expression;

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
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.Compression;
import io.opentools.formats.FormatToolSupport;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExpressionNormalizeTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "expression.normalize",
            "Normalize Expression",
            "Normalize a count matrix with RPKM, FPKM, TPM, or TAU.",
            "Expression",
            List.of("RPKM", "FPKM", "TPM", "TAU", "表达标准化"),
            List.of("Count matrix", "Gene lengths"),
            List.of("TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Count matrix", "Delimited matrix with gene names in the first column."),
                    new ToolParameter("method", "Normalization", "Expression normalization method.", ParameterType.ENUM,
                            false, "TPM", List.of("RPKM", "FPKM", "TPM", "TAU"), null, null, false, null),
                    ToolParameter.optionalFile("geneLengths", "Gene lengths", "Two-column gene/length table for RPKM, FPKM, or TPM."),
                    new ToolParameter("delimiter", "Delimiter", "Matrix delimiter.", ParameterType.ENUM,
                            false, "AUTO", List.of("AUTO", "TAB", "COMMA", "SEMICOLON"), null, null, false, null),
                    ToolParameter.bool("log2", "Log2 transform input", "Apply log2(x+1) before normalization.", false),
                    ToolParameter.optionalOutputFile("output", "Output matrix", "Leave blank to save in the run directory."),
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
        if (!"TAU".equals(request.string("method")) && request.path("geneLengths") == null) {
            result = result.merge(io.opentools.api.tool.ValidationResult.of(
                    io.opentools.api.tool.ValidationIssue.error(
                            "geneLengths", "Gene lengths are required for RPKM, FPKM, and TPM.",
                            "Choose a two-column gene/length table.")));
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        ExpressionMatrix matrix = new ExpressionMatrixParser().parse(
                request.path("input"), request.string("delimiter"), "ERROR", request.bool("log2"));
        Path output = FormatToolSupport.outputPath(request, context, "normalized-expression.tsv");
        String method = request.string("method");
        Map<String, Double> lengths = "TAU".equals(method) ? Map.of() : readLengths(request.path("geneLengths"));
        List<List<Double>> normalized;
        if ("TAU".equals(method)) {
            normalized = tau(matrix);
        } else {
            normalized = withLengths(matrix, lengths, method);
        }
        write(output, matrix, normalized);
        context.progress(1.0, "Expression normalization completed");
        return new ToolResult(
                "Normalized " + matrix.rowNames().size() + " genes by " + method,
                "result.expression.normalize.summary", List.of(matrix.rowNames().size(), method),
                matrix.columnNames().size() + " samples were normalized.",
                "result.expression.normalize.detail", List.of(matrix.columnNames().size()),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Method", method),
                        List.of("Genes", Integer.toString(matrix.rowNames().size())),
                        List.of("Samples", Integer.toString(matrix.columnNames().size()))
                )),
                List.of(new OutputArtifact(output, "Normalized expression", "text/tab-separated-values")),
                Map.of("method", method, "genes", matrix.rowNames().size(), "samples", matrix.columnNames().size())
        );
    }

    private static List<List<Double>> withLengths(
            ExpressionMatrix matrix, Map<String, Double> lengths, String method
    ) {
        int columns = matrix.columnNames().size();
        List<List<Double>> result = new ArrayList<>();
        if ("TPM".equals(method)) {
            for (int row = 0; row < matrix.rowNames().size(); row++) {
                double length = requiredLength(lengths, matrix.rowNames().get(row));
                List<Double> values = new ArrayList<>();
                for (int column = 0; column < columns; column++) {
                    values.add(matrix.value(row, column) / length);
                }
                result.add(values);
            }
            for (int column = 0; column < columns; column++) {
                double total = 0;
                for (List<Double> row : result) {
                    total += row.get(column);
                }
                for (List<Double> row : result) {
                    row.set(column, total == 0 ? 0 : row.get(column) * 1_000_000.0 / total);
                }
            }
            return result;
        }
        for (int column = 0; column < columns; column++) {
            double total = 0;
            for (int row = 0; row < matrix.rowNames().size(); row++) {
                total += matrix.value(row, column);
            }
            if (total <= 0) {
                throw new OpenToolException("INVALID_COUNT_TOTAL", "Sample " + matrix.columnNames().get(column)
                        + " has a non-positive total.", "Check count values.");
            }
            for (int row = 0; row < matrix.rowNames().size(); row++) {
                if (result.size() <= row) {
                    result.add(new ArrayList<>());
                }
                double length = requiredLength(lengths, matrix.rowNames().get(row));
                double value = matrix.value(row, column) * 1_000_000_000.0 / (length * total);
                result.get(row).add(value);
            }
        }
        return result;
    }

    private static List<List<Double>> tau(ExpressionMatrix matrix) {
        List<List<Double>> result = new ArrayList<>();
        for (int row = 0; row < matrix.rowNames().size(); row++) {
            double total = 0;
            for (int column = 0; column < matrix.columnNames().size(); column++) {
                total += Math.max(0, matrix.value(row, column));
            }
            List<Double> values = new ArrayList<>();
            for (int column = 0; column < matrix.columnNames().size(); column++) {
                values.add(total == 0 ? 0 : matrix.value(row, column) / total);
            }
            result.add(values);
        }
        return result;
    }

    private static double requiredLength(Map<String, Double> lengths, String gene) {
        Double length = lengths.get(gene);
        if (length == null || length <= 0) {
            throw new OpenToolException("GENE_LENGTH_MISSING",
                    "No positive gene length was found for " + gene + ".",
                    "Provide a length for every matrix row.");
        }
        return length;
    }

    private static Map<String, Double> readLengths(Path input) throws Exception {
        Map<String, Double> lengths = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(input), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.trim().split("[\\t,;]+");
                if (fields.length < 2) {
                    continue;
                }
                try {
                    lengths.put(fields[0], Double.parseDouble(fields[1]));
                } catch (NumberFormatException ignored) {
                    // Header or nonnumeric row.
                }
            }
        }
        return lengths;
    }

    private static void write(Path output, ExpressionMatrix matrix, List<List<Double>> values) throws Exception {
        FormatToolSupport.writeAtomically(output, writer -> {
            writer.write("gene");
            for (String sample : matrix.columnNames()) {
                writer.write('\t');
                writer.write(sample);
            }
            writer.newLine();
            for (int row = 0; row < matrix.rowNames().size(); row++) {
                writer.write(matrix.rowNames().get(row));
                for (double value : values.get(row)) {
                    writer.write('\t');
                    writer.write(String.format(Locale.ROOT, "%.10g", value));
                }
                writer.newLine();
            }
        });
    }
}
