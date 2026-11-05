package io.opentools.analysis.enrichment;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.OpenToolException;
import io.opentools.api.tool.OutputArtifact;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class GseaTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "go.gsea",
            "Preranked GSEA",
            "Run preranked gene-set enrichment with weighted running-sum scores and permutation p-values.",
            "Enrichment",
            List.of("GSEA", "preranked GSEA", "gene set enrichment", "GSEA 富集"),
            List.of("Ranked genes", "GMT gene sets"),
            List.of("TSV"),
            "1.0.0",
            List.of(
                    ToolParameter.requiredFile("ranking", "Ranked genes",
                            "Two columns: gene and numeric score, optionally with a header."),
                    ToolParameter.requiredFile("geneSets", "GMT gene sets",
                            "GMT file: set ID, description, then one or more genes."),
                    new ToolParameter("weight", "Ranking weight",
                            "GSEA running-sum weight: classic, weighted, or weighted p=2.",
                            io.opentools.api.tool.ParameterType.ENUM, false, "WEIGHTED",
                            List.of("CLASSIC", "WEIGHTED", "WEIGHTED_P2"), null, null, false, null),
                    new ToolParameter("duplicatePolicy", "Duplicate genes",
                            "Collapse duplicate ranking genes by maximum, mean, or last score.",
                            io.opentools.api.tool.ParameterType.ENUM, false, "MAX",
                            List.of("MAX", "MEAN", "LAST"), null, null, false, null),
                    ToolParameter.integer("minSetSize", "Minimum set size",
                            "Skip sets with fewer ranked genes.", 15, 1, 100_000),
                    ToolParameter.integer("maxSetSize", "Maximum set size",
                            "Skip sets with more ranked genes.", 500, 1, 1_000_000),
                    ToolParameter.integer("permutations", "Permutations",
                            "Deterministic gene-score permutations for p-values.", 1000, 10, 10_000),
                    ToolParameter.integer("seed", "Random seed",
                            "Seed for reproducible permutations.", 13, 0, Integer.MAX_VALUE),
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
        ValidationResult result = validateParameters(request)
                .merge(FormatToolSupport.validateOutput(request, request.path("ranking")));
        if (request.integer("minSetSize") > request.integer("maxSetSize")) {
            result = result.merge(ValidationResult.of(
                    io.opentools.api.tool.ValidationIssue.error(
                            "minSetSize", "Minimum set size exceeds maximum set size.",
                            "Adjust the set-size bounds.")));
        }
        return result;
    }

    @Override
    public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        List<RankedGene> ranking = readRanking(
                request.path("ranking"), request.string("duplicatePolicy"));
        if (ranking.isEmpty()) {
            throw new OpenToolException("EMPTY_RANKING", "No ranked genes were found.", "Check the ranking file.");
        }
        Map<String, Set<String>> geneSets = readGeneSets(request.path("geneSets"));
        Map<String, int[]> hitPositions = new LinkedHashMap<>();
        for (var entry : geneSets.entrySet()) {
            List<Integer> positions = new ArrayList<>();
            for (int index = 0; index < ranking.size(); index++) {
                if (entry.getValue().contains(ranking.get(index).gene())) {
                    positions.add(index);
                }
            }
            if (positions.size() >= request.integer("minSetSize")
                    && positions.size() <= request.integer("maxSetSize")) {
                hitPositions.put(entry.getKey(),
                        positions.stream().mapToInt(Integer::intValue).toArray());
            }
        }
        if (hitPositions.isEmpty()) {
            throw new OpenToolException(
                    "NO_GENE_SETS",
                    "No gene sets passed the size bounds.",
                    "Adjust minSetSize/maxSetSize or check gene identifiers."
            );
        }
        double[] scores = ranking.stream().mapToDouble(RankedGene::score).toArray();
        String weightMode = request.string("weight");
        int permutations = request.integer("permutations");
        Random random = new Random(request.integer("seed"));
        Map<String, Double> observed = new LinkedHashMap<>();
        Map<String, List<Double>> nullScores = new LinkedHashMap<>();
        for (String setId : hitPositions.keySet()) {
            observed.put(setId, enrichmentScore(hitPositions.get(setId), scores, ranking.size(), weightMode));
            nullScores.put(setId, new ArrayList<>(permutations));
        }
        double[] shuffled = new double[scores.length];
        for (int permutation = 0; permutation < permutations; permutation++) {
            context.cancellationToken().throwIfCancelled();
            System.arraycopy(scores, 0, shuffled, 0, scores.length);
            for (int index = shuffled.length - 1; index > 0; index--) {
                int swap = random.nextInt(index + 1);
                double value = shuffled[index];
                shuffled[index] = shuffled[swap];
                shuffled[swap] = value;
            }
            for (String setId : hitPositions.keySet()) {
                nullScores.get(setId).add(enrichmentScore(
                        hitPositions.get(setId), shuffled, ranking.size(), weightMode));
            }
        }
        List<Result> results = new ArrayList<>();
        for (String setId : hitPositions.keySet()) {
            List<Double> nullValues = nullScores.get(setId);
            double meanAbs = nullValues.stream().mapToDouble(Math::abs).average().orElse(1);
            double es = observed.get(setId);
            double nes = meanAbs == 0 ? 0 : es / meanAbs;
            long extreme = nullValues.stream().filter(value -> Math.abs(value) >= Math.abs(es)).count();
            double p = (extreme + 1.0) / (permutations + 1.0);
            results.add(new Result(setId, hitPositions.get(setId).length, es, nes, p, p));
        }
        results.sort(Comparator.comparingDouble(Result::pValue).thenComparing(Result::setId));
        applyBh(results);
        Path output = FormatToolSupport.outputPath(request, context, "gsea.tsv");
        FormatToolSupport.writeAtomically(output, writer -> {
            writer.write("set_id\tset_size\tenrichment_score\tnes\tp_value\tadjusted_p_value\n");
            for (Result result : results) {
                writer.write(result.setId() + "\t" + result.size() + "\t"
                        + format(result.es()) + "\t" + format(result.nes()) + "\t"
                        + format(result.pValue()) + "\t" + format(result.adjustedPValue()) + "\n");
            }
        });
        context.progress(1.0, "GSEA completed");
        return new ToolResult(
                "Tested " + results.size() + " gene sets",
                List.of(results.size()),
                results.stream().filter(result -> result.adjustedPValue() < 0.05).count()
                        + " sets with adjusted p < 0.05.",
                List.of(),
                new ResultTable(List.of("Set", "NES", "Adjusted P"), results.stream().limit(50)
                        .map(result -> List.of(result.setId(), format(result.nes()),
                                format(result.adjustedPValue()))).toList()),
                List.of(new OutputArtifact(output, "GSEA results", "text/tab-separated-values")),
                Map.of("sets", results.size(), "permutations", permutations, "seed", request.integer("seed"))
        );
    }

    private static List<RankedGene> readRanking(Path path, String duplicatePolicy) throws Exception {
        Map<String, List<Double>> collected = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(path), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.trim().split("[\t,]+");
                if (fields.length < 2) {
                    throw new OpenToolException("GSEA_RANKING_FORMAT",
                            "Ranking rows need gene and score columns.", "Use a two-column TSV.");
                }
                try {
                    double score = Double.parseDouble(fields[1]);
                    if (first && fields[0].equalsIgnoreCase("gene")) {
                        first = false;
                        continue;
                    }
                    first = false;
                    collected.computeIfAbsent(fields[0], ignored -> new ArrayList<>()).add(score);
                } catch (NumberFormatException ex) {
                    if (first) {
                        first = false;
                        continue;
                    }
                    throw new OpenToolException("GSEA_RANKING_FORMAT",
                            "Non-numeric ranking score: " + fields[1], "Use numeric scores.");
                }
            }
        }
        List<RankedGene> ranking = new ArrayList<>();
        for (var entry : collected.entrySet()) {
            List<Double> values = entry.getValue();
            double score = switch (duplicatePolicy) {
                case "MEAN" -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                case "LAST" -> values.getLast();
                default -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            };
            ranking.add(new RankedGene(entry.getKey(), score));
        }
        ranking.sort(Comparator.comparingDouble(RankedGene::score).reversed());
        return ranking;
    }

    private static Map<String, Set<String>> readGeneSets(Path path) throws Exception {
        Map<String, Set<String>> sets = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length < 3) {
                    throw new OpenToolException("GMT_FORMAT",
                            "GMT rows need set ID, description, and at least one gene.",
                            "Export the gene sets in GMT format.");
                }
                Set<String> genes = new LinkedHashSet<>();
                for (int index = 2; index < fields.length; index++) {
                    if (!fields[index].isBlank()) genes.add(fields[index].trim());
                }
                if (!genes.isEmpty()) sets.put(fields[0].trim(), genes);
            }
        }
        return sets;
    }

    private static double enrichmentScore(
            int[] positions,
            double[] scores,
            int total,
            String weightMode
    ) {
        int hits = positions.length;
        if (hits == 0 || hits == total) return 0;
        double hitWeight = 0;
        for (int index : positions) hitWeight += weightFor(scores[index], weightMode);
        boolean zeroWeight = hitWeight == 0;
        if (zeroWeight) hitWeight = hits;
        double running = 0;
        double maximum = Double.NEGATIVE_INFINITY;
        double minimum = Double.POSITIVE_INFINITY;
        for (int index = 0; index < hits; index++) {
            int position = positions[index];
            running += (zeroWeight ? 1.0 : weightFor(scores[position], weightMode)) / hitWeight;
            running -= (double) (position - index) / (total - hits);
            maximum = Math.max(maximum, running);
            minimum = Math.min(minimum, running);
            int next = index + 1 < hits ? positions[index + 1] : total;
            double afterGap = running - (double) (next - position - 1) / (total - hits);
            minimum = Math.min(minimum, afterGap);
        }
        return Math.abs(maximum) >= Math.abs(minimum) ? maximum : minimum;
    }

    private static double weightFor(double score, String mode) {
        return switch (mode) {
            case "CLASSIC" -> 1;
            case "WEIGHTED_P2" -> score * score;
            default -> Math.abs(score);
        };
    }

    private static void applyBh(List<Result> results) {
        double previous = 1;
        for (int index = results.size() - 1; index >= 0; index--) {
            Result result = results.get(index);
            previous = Math.min(previous, result.pValue() * results.size() / (index + 1.0));
            results.set(index, new Result(result.setId(), result.size(), result.es(), result.nes(),
                    result.pValue(), Math.min(1, previous)));
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.10g", value);
    }

    private record RankedGene(String gene, double score) { }

    private record Result(String setId, int size, double es, double nes, double pValue, double adjustedPValue) { }
}
