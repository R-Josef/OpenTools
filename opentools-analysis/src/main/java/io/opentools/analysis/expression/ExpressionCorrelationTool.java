package io.opentools.analysis.expression;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.core.util.OrderedParallelProcessor;
import io.opentools.formats.FormatToolSupport;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExpressionCorrelationTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "expression.correlation",
            "Expression Correlation",
            "Calculate Pearson or Spearman sample correlation from an expression matrix.",
            "Expression",
            List.of("sample correlation", "Pearson", "Spearman", "表达相关"),
            List.of("Expression matrix"),
            List.of("TSV"),
            "1.1.0",
            List.of(
                    ToolParameter.requiredFile("input", "Expression matrix", "Delimited matrix with genes in the first column."),
                    ToolParameter.integer("threads", "Threads",
                            "0 uses AUTO for large matrices; 1 disables parallel correlation rows.",
                            0, 0, 64),
                    new ToolParameter("method", "Correlation method", "Correlation coefficient.", ParameterType.ENUM,
                            false, "PEARSON", List.of("PEARSON", "SPEARMAN"), null, null, false, null),
                    new ToolParameter("delimiter", "Delimiter", "Matrix delimiter.", ParameterType.ENUM,
                            false, "AUTO", List.of("AUTO", "TAB", "COMMA", "SEMICOLON"), null, null, false, null),
                    ToolParameter.bool("log2", "Log2 transform input", "Apply log2(x+1) before correlation.", false),
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
        return validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("input")));
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        ExpressionMatrix matrix = new ExpressionMatrixParser().parse(
                request.path("input"), request.string("delimiter"), "ERROR", request.bool("log2"));
        Path output = FormatToolSupport.outputPath(request, context, "sample-correlation.tsv");
        int count = matrix.columnNames().size();
        int threads = OrderedParallelProcessor.resolveThreads(
                request.integer("threads"), Files.size(request.path("input")));
        List<List<Double>> columns = new ArrayList<>();
        for (int column = 0; column < count; column++) {
            List<Double> values = new ArrayList<>();
            for (int row = 0; row < matrix.rowNames().size(); row++) {
                values.add(matrix.value(row, column));
            }
            columns.add("SPEARMAN".equals(request.string("method")) ? ranks(values) : values);
        }
        double[][] correlation = new double[count][count];
        int[] nextRow = {0};
        OrderedParallelProcessor.processChunks(
                threads,
                8,
                context.cancellationToken(),
                () -> nextRow[0] < count ? nextRow[0]++ : null,
                rows -> {
                    double[][] values = new double[rows.size()][];
                    for (int index = 0; index < rows.size(); index++) {
                        int a = rows.get(index);
                        double[] row = new double[count];
                        for (int b = 0; b < count; b++) {
                            row[b] = pearson(columns.get(a), columns.get(b));
                        }
                        values[index] = row;
                    }
                    return new CorrelationRows(rows.getFirst(), values);
                },
                rows -> {
                    for (int index = 0; index < rows.values().length; index++) {
                        correlation[rows.startRow() + index] = rows.values()[index];
                    }
                }
        );
        FormatToolSupport.writeAtomically(output, writer -> writeMatrix(writer, matrix.columnNames(), correlation));
        context.progress(1.0, "Expression correlation completed");
        return new ToolResult(
                "Calculated " + count + " x " + count + " sample correlation", List.of(count, request.string("method")),
                "Coefficients were calculated from " + matrix.rowNames().size() + " genes.", List.of(matrix.rowNames().size()),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Method", request.string("method")),
                        List.of("Samples", Integer.toString(count)),
                        List.of("Genes", Integer.toString(matrix.rowNames().size()))
                )),
                List.of(new OutputArtifact(output, "Sample correlation matrix", "text/tab-separated-values")),
                Map.of("method", request.string("method"), "samples", count,
                        "genes", matrix.rowNames().size(), "threads", threads,
                        "parallelRows", threads > 1)
        );
    }

    private static void writeMatrix(BufferedWriter writer, List<String> names, double[][] values) throws Exception {
        writer.write("sample");
        for (String name : names) {
            writer.write('\t');
            writer.write(name);
        }
        writer.newLine();
        for (int row = 0; row < names.size(); row++) {
            writer.write(names.get(row));
            for (int column = 0; column < names.size(); column++) {
                writer.write('\t');
                writer.write(String.format(Locale.ROOT, "%.10g", values[row][column]));
            }
            writer.newLine();
        }
    }

    static double pearson(List<Double> x, List<Double> y) {
        double meanX = x.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double meanY = y.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double numerator = 0;
        double sx = 0;
        double sy = 0;
        for (int index = 0; index < x.size(); index++) {
            double dx = x.get(index) - meanX;
            double dy = y.get(index) - meanY;
            numerator += dx * dy;
            sx += dx * dx;
            sy += dy * dy;
        }
        return sx == 0 || sy == 0 ? 0 : numerator / Math.sqrt(sx * sy);
    }

    static List<Double> ranks(List<Double> values) {
        Map<Integer, Double> sorted = new LinkedHashMap<>();
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            order.add(index);
        }
        order.sort(Comparator.comparingDouble(values::get));
        double[] ranks = new double[values.size()];
        for (int index = 0; index < order.size(); ) {
            int end = index + 1;
            while (end < order.size() && Double.compare(values.get(order.get(end)),
                    values.get(order.get(index))) == 0) {
                end++;
            }
            double rank = (index + 1 + end) / 2.0;
            for (int current = index; current < end; current++) {
                ranks[order.get(current)] = rank;
            }
            index = end;
        }
        List<Double> result = new ArrayList<>();
        for (double rank : ranks) {
            result.add(rank);
        }
        return result;
    }

    private record CorrelationRows(int startRow, double[][] values) {
    }
}
