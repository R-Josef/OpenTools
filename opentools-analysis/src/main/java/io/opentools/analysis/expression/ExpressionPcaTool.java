package io.opentools.analysis.expression;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
import io.opentools.api.tool.ResultTable;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.formats.FormatTaskSupport;

import java.io.BufferedWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExpressionPcaTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "expression.pca",
            "Expression PCA",
            "Center and optionally scale sample expression profiles and calculate principal components.",
            "Expression",
            List.of("PCA", "principal component", "expression PCA", "表达主成分"),
            List.of("Expression matrix"),
            List.of("TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Expression matrix", "Delimited matrix with genes in the first column."),
                    ToolParameter.optionalFile("groups", "Sample groups", "Optional two-column sample/group file."),
                    ToolParameter.integer("components", "Principal components", "Maximum components to output.", 2, 1, 50),
                    new ToolParameter("delimiter", "Delimiter", "Matrix delimiter.", io.opentools.api.tool.ParameterType.ENUM,
                            false, "AUTO", List.of("AUTO", "TAB", "COMMA", "SEMICOLON"), null, null, false, null),
                    ToolParameter.bool("scale", "Scale features", "Divide each gene by its standard deviation.", true),
                    ToolParameter.bool("log2", "Log2 transform input", "Apply log2(x+1) before PCA.", false),
                    ToolParameter.optionalOutputFile("output", "Output coordinates", "Leave blank to save in the run directory."),
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
                .merge(FormatTaskSupport.validateOutput(request, request.path("input")));
    }

    @Override
    public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        ExpressionMatrix matrix = new ExpressionMatrixParser().parse(
                request.path("input"), request.string("delimiter"), "ERROR", request.bool("log2"));
        int samples = matrix.columnNames().size();
        if (samples < 2) {
            throw new OpenToolException("PCA_TOO_FEW_SAMPLES", "PCA requires at least two samples.", "Add samples.");
        }
        double[][] z = samplesByFeatures(matrix, request.bool("scale"));
        double[][] covariance = covariance(z, samples);
        Eigen eigen = jacobi(covariance);
        Map<String, String> groups = request.path("groups") == null
                ? Map.of() : readGroups(request.path("groups"));
        int components = Math.min(request.integer("components"), Math.min(samples, eigen.values.length));
        List<Double> variance = new ArrayList<>();
        for (int index = 0; index < eigen.values.length; index++) {
            if (eigen.values[index] > 0) {
                variance.add(eigen.values[index]);
            }
        }
        double varianceTotal = variance.stream().mapToDouble(Double::doubleValue).sum();
        Path output = FormatTaskSupport.outputPath(request, context, "pca-coordinates.tsv");
        FormatTaskSupport.writeAtomically(output, writer -> {
            writer.write("sample\tgroup");
            for (int component = 0; component < components; component++) {
                writer.write("\tPC" + (component + 1));
            }
            writer.write("\tvariance_explained\n");
            for (int sample = 0; sample < samples; sample++) {
                writer.write(matrix.columnNames().get(sample));
                writer.write('\t');
                writer.write(groups.getOrDefault(matrix.columnNames().get(sample), ""));
                for (int component = 0; component < components; component++) {
                    double coordinate = eigen.vectors[sample][component]
                            * Math.sqrt(Math.max(0, eigen.values[component]));
                    writer.write('\t');
                    writer.write(String.format(Locale.ROOT, "%.10g", coordinate));
                }
                writer.write('\t');
                writer.write(String.format(Locale.ROOT, "%.10g",
                        varianceTotal == 0 ? 0 : percentage(eigen.values, sample, varianceTotal)));
                writer.newLine();
            }
        });
        context.progress(1.0, "Expression PCA completed");
        return new TaskResult(
                "Calculated " + components + " principal components for " + samples + " samples",
                "result.expression.pca.summary", List.of(samples, components),
                "PC1 explains " + (varianceTotal == 0 ? "0" :
                        String.format(Locale.ROOT, "%.2f", eigen.values[0] * 100 / varianceTotal)) + "% of variance.",
                "result.expression.pca.detail", List.of(
                        varianceTotal == 0 ? 0.0 : eigen.values[0] * 100 / varianceTotal),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Samples", Integer.toString(samples)),
                        List.of("Components", Integer.toString(components)),
                        List.of("Genes", Integer.toString(matrix.rowNames().size()))
                )),
                List.of(new OutputArtifact(output, "PCA coordinates", "text/tab-separated-values")),
                Map.of("samples", samples, "components", components, "genes", matrix.rowNames().size())
        );
    }

    private static double[][] samplesByFeatures(ExpressionMatrix matrix, boolean scale) {
        int samples = matrix.columnNames().size();
        int genes = matrix.rowNames().size();
        double[][] z = new double[samples][genes];
        for (int gene = 0; gene < genes; gene++) {
            double mean = 0;
            for (int sample = 0; sample < samples; sample++) {
                mean += matrix.value(gene, sample);
            }
            mean /= samples;
            double sd = 0;
            for (int sample = 0; sample < samples; sample++) {
                double diff = matrix.value(gene, sample) - mean;
                sd += diff * diff;
                z[sample][gene] = diff;
            }
            sd = Math.sqrt(sd / Math.max(1, samples - 1));
            if (scale && sd > 0) {
                for (int sample = 0; sample < samples; sample++) {
                    z[sample][gene] /= sd;
                }
            }
        }
        return z;
    }

    private static double[][] covariance(double[][] z, int samples) {
        double[][] covariance = new double[samples][samples];
        for (int a = 0; a < samples; a++) {
            for (int b = a; b < samples; b++) {
                double sum = 0;
                for (int feature = 0; feature < z[a].length; feature++) {
                    sum += z[a][feature] * z[b][feature];
                }
                covariance[a][b] = covariance[b][a] = sum / Math.max(1, z[a].length - 1);
            }
        }
        return covariance;
    }

    private static Eigen jacobi(double[][] input) {
        int n = input.length;
        double[][] a = new double[n][n];
        double[][] vectors = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(input[i], 0, a[i], 0, n);
            vectors[i][i] = 1;
        }
        for (int iteration = 0; iteration < 100 * n * n; iteration++) {
            int p = 0;
            int q = 1;
            double max = 0;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (Math.abs(a[i][j]) > max) {
                        max = Math.abs(a[i][j]);
                        p = i;
                        q = j;
                    }
                }
            }
            if (max < 1e-12) {
                break;
            }
            double theta = (a[q][q] - a[p][p]) / (2 * a[p][q]);
            double t = Math.signum(theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1));
            if (theta == 0) {
                t = 1;
            }
            double c = 1 / Math.sqrt(t * t + 1);
            double s = t * c;
            for (int k = 0; k < n; k++) {
                double akp = a[k][p];
                double akq = a[k][q];
                a[k][p] = c * akp - s * akq;
                a[k][q] = s * akp + c * akq;
            }
            for (int k = 0; k < n; k++) {
                double apk = a[p][k];
                double aqk = a[q][k];
                a[p][k] = c * apk - s * aqk;
                a[q][k] = s * apk + c * aqk;
            }
            for (int k = 0; k < n; k++) {
                double vkp = vectors[k][p];
                double vkq = vectors[k][q];
                vectors[k][p] = c * vkp - s * vkq;
                vectors[k][q] = s * vkp + c * vkq;
            }
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> a[i][i]).reversed());
        double[] values = new double[n];
        double[][] orderedVectors = new double[n][n];
        for (int i = 0; i < n; i++) {
            values[i] = Math.max(0, a[order[i]][order[i]]);
            for (int k = 0; k < n; k++) {
                orderedVectors[k][i] = vectors[k][order[i]];
            }
        }
        return new Eigen(values, orderedVectors);
    }

    private static Map<String, String> readGroups(Path input) throws Exception {
        Map<String, String> groups = new java.util.LinkedHashMap<>();
        try (var reader = java.nio.file.Files.newBufferedReader(input, java.nio.charset.StandardCharsets.UTF_8)) {
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

    private static double percentage(double[] values, int component, double total) {
        for (int index = 0; index < values.length; index++) {
            if (index == component) {
                return values[index] * 100 / total;
            }
        }
        return 0;
    }

    private record Eigen(double[] values, double[][] vectors) {
    }
}
