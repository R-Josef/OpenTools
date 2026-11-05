package io.opentools.analysis.expression;

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
import io.opentools.formats.FormatToolSupport;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExpressionDifferentialTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "expression.differential",
            "Differential Expression Test",
            "Compare two sample groups with Welch t-test or Mann-Whitney U and BH-adjusted p-values.",
            "Expression",
            List.of("differential expression", "Welch t-test", "Mann-Whitney", "差异表达", "差异检验"),
            List.of("Expression matrix", "Sample groups"),
            List.of("TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("input", "Expression matrix",
                            "Genes in the first column and samples in the header."),
                    ToolParameter.requiredFile("groups", "Sample groups",
                            "Two-column sample/group file with exactly two groups."),
                    new ToolParameter("test", "Statistical test",
                            "Two-group test to run.", ParameterType.ENUM,
                            false, "WELCH_T", List.of("WELCH_T", "MANN_WHITNEY", "ANOVA"),
                            null, null, false, null),
                    new ToolParameter("delimiter", "Delimiter", "Matrix delimiter.", ParameterType.ENUM,
                            false, "AUTO", List.of("AUTO", "TAB", "COMMA", "SEMICOLON"), null, null, false, null),
                    ToolParameter.bool("log2", "Log2 transform values",
                            "Apply log2(x+1) before testing.", false),
                    new ToolParameter("adjustment", "P-value adjustment",
                            "Multiple-testing correction.", ParameterType.ENUM,
                            false, "BH", List.of("BH", "NONE"), null, null, false, null),
                    ToolParameter.optionalOutputFile("output", "Output TSV",
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
        return validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("input")));
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        ExpressionMatrix matrix = new ExpressionMatrixParser().parse(
                request.path("input"), request.string("delimiter"), "ERROR", false);
        Map<String, String> groups = readGroups(request.path("groups"));
        List<String> groupNames = groups.values().stream().distinct().toList();
        String test = request.string("test");
        boolean anova = test.equals("ANOVA");
        if (groupNames.size() < 2 || (!anova && groupNames.size() != 2)) {
            throw new OpenToolException(
                    "DIFFERENTIAL_GROUPS",
                    anova
                            ? "ANOVA requires at least two sample groups."
                            : "Exactly two sample groups are required, found " + groupNames.size() + ".",
                    anova
                            ? "Provide at least two groups in the sample/group file."
                            : "Provide a two-column sample/group file with two groups."
            );
        }
        String groupA = groupNames.get(0);
        String groupB = groupNames.get(1);
        List<Integer> samplesA = sampleIndexes(matrix, groups, groupA);
        List<Integer> samplesB = sampleIndexes(matrix, groups, groupB);
        if (samplesA.size() < 2 || samplesB.size() < 2) {
            throw new OpenToolException(
                    "DIFFERENTIAL_REPLICATES",
                    "Each group needs at least two samples.",
                    "Add biological replicates or use a descriptive summary instead."
            );
        }
        List<List<Integer>> anovaSamples = new ArrayList<>();
        if (anova) {
            for (String group : groupNames) {
                List<Integer> samples = sampleIndexes(matrix, groups, group);
                if (samples.size() < 2) {
                    throw new OpenToolException(
                            "DIFFERENTIAL_REPLICATES",
                            "Each ANOVA group needs at least two samples.",
                            "Add biological replicates or use a descriptive summary instead."
                    );
                }
                anovaSamples.add(samples);
            }
        }
        boolean log2 = request.bool("log2");
        List<GeneResult> results = new ArrayList<>();
        Map<String, String> groupMeans = new LinkedHashMap<>();
        for (int row = 0; row < matrix.rowNames().size(); row++) {
            context.cancellationToken().throwIfCancelled();
            if (anova) {
                List<double[]> groupsData = new ArrayList<>();
                StringBuilder means = new StringBuilder();
                for (int index = 0; index < anovaSamples.size(); index++) {
                    double[] values = values(matrix, row, anovaSamples.get(index), log2);
                    groupsData.add(values);
                    if (index > 0) means.append(';');
                    means.append(groupNames.get(index)).append('=').append(format(mean(values)));
                }
                groupMeans.put(matrix.rowNames().get(row), means.toString());
                results.add(anova(matrix.rowNames().get(row), groupsData));
            } else {
                double[] a = values(matrix, row, samplesA, log2);
                double[] b = values(matrix, row, samplesB, log2);
                GeneResult result = test.equals("MANN_WHITNEY")
                        ? mannWhitney(matrix.rowNames().get(row), a, b)
                        : welchT(matrix.rowNames().get(row), a, b);
                results.add(result);
                groupMeans.put(matrix.rowNames().get(row), "");
            }
        }
        boolean bh = request.string("adjustment").equals("BH");
        if (bh) {
            applyBenjaminiHochberg(results);
        }
        Path output = FormatToolSupport.outputPath(request, context, "differential-expression.tsv");
        FormatToolSupport.writeAtomically(output, writer -> {
            writer.write("gene\tgroup_a\tgroup_b\tn_a\tn_b\tmean_a\tmean_b"
                    + "\tlog2_fold_change\tstatistic\tp_value\tadjusted_p_value\ttest\tgroup_means\n");
            for (GeneResult result : results) {
                writer.write(result.gene() + "\t"
                        + (anova ? "" : groupA) + "\t"
                        + (anova ? "" : groupB) + "\t"
                        + (anova ? "" : Integer.toString(result.nA())) + "\t"
                        + (anova ? "" : Integer.toString(result.nB())) + "\t"
                        + (anova ? "" : format(result.meanA())) + "\t"
                        + (anova ? "" : format(result.meanB())) + "\t"
                        + format(result.log2FoldChange()) + "\t" + format(result.statistic()) + "\t"
                        + format(result.pValue()) + "\t" + format(result.adjustedPValue()) + "\t"
                        + test + "\t" + groupMeans.getOrDefault(result.gene(), "") + "\n");
            }
        });
        long significant = results.stream().filter(result -> result.adjustedPValue() < 0.05).count();
        context.progress(1.0, "Differential expression completed");
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("genes", results.size());
        metrics.put("groupA", groupA);
        metrics.put("groupB", groupB);
        metrics.put("groupCount", groupNames.size());
        metrics.put("test", test);
        metrics.put("significant", significant);
        return new ToolResult(
                anova
                        ? "Tested " + results.size() + " genes across " + groupNames.size() + " groups"
                        : "Tested " + results.size() + " genes between " + groupA + " and " + groupB,
                List.of(results.size()),
                significant + " genes with adjusted p < 0.05.",
                List.of(significant),
                new ResultTable(List.of("Metric", "Value"), List.of(
                        List.of("Genes", Integer.toString(results.size())),
                        List.of("Group A", groupA),
                        List.of("Group B", groupB),
                        List.of("Test", test),
                        List.of("Adjusted p < 0.05", Long.toString(significant))
                )),
                List.of(new OutputArtifact(output, "Differential expression results",
                        "text/tab-separated-values")),
                metrics
        );
    }

    private static List<Integer> sampleIndexes(
            ExpressionMatrix matrix,
            Map<String, String> groups,
            String group
    ) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < matrix.columnNames().size(); index++) {
            if (group.equals(groups.get(matrix.columnNames().get(index)))) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    private static double[] values(ExpressionMatrix matrix, int row, List<Integer> samples, boolean log2) {
        double[] values = new double[samples.size()];
        for (int index = 0; index < samples.size(); index++) {
            double value = matrix.value(row, samples.get(index));
            values[index] = log2 ? Math.log(value + 1) / Math.log(2) : value;
        }
        return values;
    }

    private static GeneResult anova(String gene, List<double[]> groups) {
        int total = groups.stream().mapToInt(values -> values.length).sum();
        double grandMean = 0;
        for (double[] values : groups) grandMean += sum(values);
        grandMean /= total;
        double between = 0;
        double within = 0;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (double[] values : groups) {
            double mean = mean(values);
            between += values.length * (mean - grandMean) * (mean - grandMean);
            within += variance(values, mean) * (values.length - 1);
            minimum = Math.min(minimum, mean);
            maximum = Math.max(maximum, mean);
        }
        int dfBetween = groups.size() - 1;
        int dfWithin = total - groups.size();
        double statistic = within == 0 ? 0 : (between / dfBetween) / (within / dfWithin);
        double p = within == 0 ? 1 : regularizedIncompleteBeta(
                dfWithin / 2.0, dfBetween / 2.0, dfWithin / (dfWithin + dfBetween * statistic));
        return new GeneResult(gene, total, 0, grandMean, 0, maximum - minimum, statistic, p, p);
    }

    private static double sum(double[] values) {
        double sum = 0;
        for (double value : values) sum += value;
        return sum;
    }

    private static GeneResult welchT(String gene, double[] a, double[] b) {
        double meanA = mean(a);
        double meanB = mean(b);
        double varA = variance(a, meanA);
        double varB = variance(b, meanB);
        double denominator = Math.sqrt(varA / a.length + varB / b.length);
        double statistic = denominator == 0 ? 0 : (meanA - meanB) / denominator;
        double dfNumerator = Math.pow(varA / a.length + varB / b.length, 2);
        double dfDenominator = Math.pow(varA / a.length, 2) / (a.length - 1)
                + Math.pow(varB / b.length, 2) / (b.length - 1);
        double df = dfDenominator == 0 ? a.length + b.length - 2 : dfNumerator / dfDenominator;
        double p = denominator == 0 ? 1 : studentTTwoSidedP(statistic, df);
        return new GeneResult(gene, a.length, b.length, meanA, meanB, meanA - meanB, statistic, p, p);
    }

    private static GeneResult mannWhitney(String gene, double[] a, double[] b) {
        int n = a.length + b.length;
        double[] combined = new double[n];
        System.arraycopy(a, 0, combined, 0, a.length);
        System.arraycopy(b, 0, combined, a.length, b.length);
        int[] ranks = ranks(combined);
        double rankSumA = 0;
        for (int index = 0; index < a.length; index++) {
            rankSumA += ranks[index];
        }
        double u = rankSumA - a.length * (a.length + 1) / 2.0;
        double mean = a.length * b.length / 2.0;
        double tieCorrection = tieCorrection(combined);
        double variance = a.length * b.length / 12.0 * ((n + 1) - tieCorrection / (n * (n - 1.0)));
        double z = variance <= 0 ? 0 : (u - mean) / Math.sqrt(variance);
        double p = variance <= 0 ? 1 : erfc(Math.abs(z) / Math.sqrt(2));
        return new GeneResult(gene, a.length, b.length, mean(a), mean(b), mean(a) - mean(b), u, p, p);
    }

    private static int[] ranks(double[] values) {
        Integer[] order = new Integer[values.length];
        for (int index = 0; index < values.length; index++) {
            order[index] = index;
        }
        java.util.Arrays.sort(order, (left, right) -> Double.compare(values[left], values[right]));
        int[] ranks = new int[values.length];
        int index = 0;
        while (index < order.length) {
            int end = index + 1;
            while (end < order.length && Double.compare(values[order[end]], values[order[index]]) == 0) {
                end++;
            }
            double rank = (index + 1 + end) / 2.0;
            for (int cursor = index; cursor < end; cursor++) {
                ranks[order[cursor]] = (int) Math.round(rank);
            }
            index = end;
        }
        return ranks;
    }

    private static double tieCorrection(double[] values) {
        Map<Double, Integer> counts = new LinkedHashMap<>();
        for (double value : values) {
            counts.merge(value, 1, Integer::sum);
        }
        double correction = 0;
        for (int count : counts.values()) {
            correction += (double) count * count * count - count;
        }
        return correction;
    }

    private static void applyBenjaminiHochberg(List<GeneResult> results) {
        List<GeneResult> ordered = new ArrayList<>(results);
        ordered.sort(java.util.Comparator.comparingDouble(GeneResult::pValue));
        double previous = 1;
        for (int index = ordered.size() - 1; index >= 0; index--) {
            GeneResult result = ordered.get(index);
            previous = Math.min(previous, result.pValue() * ordered.size() / (index + 1.0));
            results.set(results.indexOf(result), new GeneResult(
                    result.gene(), result.nA(), result.nB(), result.meanA(), result.meanB(),
                    result.log2FoldChange(), result.statistic(), result.pValue(),
                    Math.min(1, previous)
            ));
        }
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static double variance(double[] values, double mean) {
        if (values.length < 2) {
            return 0;
        }
        double sum = 0;
        for (double value : values) {
            sum += (value - mean) * (value - mean);
        }
        return sum / (values.length - 1.0);
    }

    private static double studentTTwoSidedP(double t, double df) {
        if (Double.isNaN(t) || df <= 0) {
            return 1;
        }
        return regularizedIncompleteBeta(df / 2.0, 0.5, df / (df + t * t));
    }

    private static double regularizedIncompleteBeta(double a, double b, double x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        double bt = Math.exp(logGamma(a + b) - logGamma(a) - logGamma(b)
                + a * Math.log(x) + b * Math.log1p(-x));
        if (x < (a + 1) / (a + b + 2)) {
            return bt * betaContinuedFraction(a, b, x) / a;
        }
        return 1 - bt * betaContinuedFraction(b, a, 1 - x) / b;
    }

    private static double betaContinuedFraction(double a, double b, double x) {
        final int maxIterations = 200;
        final double epsilon = 3e-12;
        final double tiny = 1e-300;
        double qab = a + b;
        double qap = a + 1;
        double qam = a - 1;
        double c = 1;
        double d = 1 - qab * x / qap;
        if (Math.abs(d) < tiny) d = tiny;
        d = 1 / d;
        double h = d;
        for (int m = 1; m <= maxIterations; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < tiny) d = tiny;
            c = 1 + aa / c;
            if (Math.abs(c) < tiny) c = tiny;
            d = 1 / d;
            h *= d * c;
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < tiny) d = tiny;
            c = 1 + aa / c;
            if (Math.abs(c) < tiny) c = tiny;
            d = 1 / d;
            double delta = d * c;
            h *= delta;
            if (Math.abs(delta - 1) < epsilon) break;
        }
        return h;
    }

    private static double logGamma(double x) {
        double[] coefficients = {
                76.18009172947146, -86.50532032941677, 24.01409824083091,
                -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5
        };
        double y = x;
        double tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double ser = 1.000000000190015;
        for (double coefficient : coefficients) {
            y += 1;
            ser += coefficient / y;
        }
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }

    private static double erfc(double x) {
        double z = Math.abs(x);
        double t = 1 / (1 + 0.5 * z);
        double result = t * Math.exp(-z * z - 1.26551223 + t * (1.00002368
                + t * (0.37409196 + t * (0.09678418 + t * (-0.18628806
                + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587
                + t * (-0.82215223 + t * 0.17087277)))))))));
        return x >= 0 ? result : 2 - result;
    }

    private static Map<String, String> readGroups(Path path) throws Exception {
        Map<String, String> groups = new LinkedHashMap<>();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.trim().split("[\t,;]+");
                if (fields.length >= 2 && !fields[0].equalsIgnoreCase("sample")) {
                    groups.put(fields[0], fields[1]);
                }
            }
        }
        return groups;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.10g", value);
    }

    private record GeneResult(
            String gene,
            int nA,
            int nB,
            double meanA,
            double meanB,
            double log2FoldChange,
            double statistic,
            double pValue,
            double adjustedPValue
    ) {
    }
}
