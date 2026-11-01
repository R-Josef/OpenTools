package io.opentools.analysis.enrichment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EnrichmentEngine {
    public List<Result> enrich(
            Map<String, Set<String>> universeAnnotations,
            Set<String> backgroundGenes,
            Set<String> targetGenes
    ) {
        Set<String> background = backgroundGenes.isEmpty()
                ? new LinkedHashSet<>(universeAnnotations.keySet())
                : new LinkedHashSet<>(backgroundGenes);
        background.retainAll(universeAnnotations.keySet());
        targetGenes = targetGenes.stream().filter(background::contains)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, Set<String>> termGenes = new LinkedHashMap<>();
        for (String gene : background) {
            for (String term : universeAnnotations.getOrDefault(gene, Set.of())) {
                termGenes.computeIfAbsent(term, ignored -> new LinkedHashSet<>()).add(gene);
            }
        }
        List<Result> results = new ArrayList<>();
        int universe = background.size();
        int target = targetGenes.size();
        for (Map.Entry<String, Set<String>> entry : termGenes.entrySet()) {
            int termSize = entry.getValue().size();
            List<String> hits = targetGenes.stream()
                    .filter(entry.getValue()::contains).sorted().toList();
            int hitCount = hits.size();
            if (hitCount == 0) {
                continue;
            }
            double p = hypergeometric(universe, termSize, target, hitCount);
            double expected = target == 0 ? 0 : target * termSize / (double) Math.max(1, universe);
            results.add(new Result(entry.getKey(), hitCount, termSize, universe, target,
                    p, p, expected == 0 ? 0 : hitCount / expected, hits));
        }
        bh(results);
        return results.stream().sorted(Comparator.comparingDouble(Result::adjustedP)
                .thenComparing(Result::term)).toList();
    }

    static double hypergeometric(int population, int successes, int draws, int observed) {
        int max = Math.min(draws, successes);
        double probability = 0;
        for (int value = observed; value <= max; value++) {
            int failures = draws - value;
            if (failures < 0 || failures > population - successes) {
                continue;
            }
            double log = logChoose(successes, value)
                    + logChoose(population - successes, failures)
                    - logChoose(population, draws);
            probability += Math.exp(log);
        }
        return Math.min(1, Math.max(0, probability));
    }

    private static double logChoose(int n, int k) {
        if (k < 0 || k > n) {
            return Double.NEGATIVE_INFINITY;
        }
        return logGamma(n + 1) - logGamma(k + 1) - logGamma(n - k + 1);
    }

    private static double logGamma(double value) {
        double[] coefficients = {
                676.5203681218851, -1259.1392167224028, 771.32342877765313,
                -176.61502916214059, 12.507343278686905, -0.13857109526572012,
                9.9843695780195716e-6, 1.5056327351493116e-7
        };
        if (value < 0.5) {
            return Math.log(Math.PI / Math.sin(Math.PI * value)) - logGamma(1 - value);
        }
        value -= 1;
        double x = 0.99999999999980993;
        for (int i = 0; i < coefficients.length; i++) {
            x += coefficients[i] / (value + i + 1);
        }
        double t = value + coefficients.length - 0.5;
        return 0.5 * Math.log(2 * Math.PI) + (value + 0.5) * Math.log(t)
                - t + Math.log(x);
    }

    private static void bh(List<Result> results) {
        List<Result> ordered = results.stream()
                .sorted(Comparator.comparingDouble(Result::pValue)).toList();
        double previous = 1;
        for (int index = ordered.size() - 1; index >= 0; index--) {
            Result result = ordered.get(index);
            double adjusted = Math.min(previous, result.pValue() * ordered.size() / (index + 1.0));
            result.adjustedP = Math.min(1, adjusted);
            previous = result.adjustedP;
        }
    }

    public static final class Result {
        private final String term;
        private final int hitCount;
        private final int termSize;
        private final int backgroundSize;
        private final int targetSize;
        private final double pValue;
        private double adjustedP;
        private final double foldEnrichment;
        private final List<String> genes;

        Result(String term, int hitCount, int termSize, int backgroundSize, int targetSize,
               double pValue, double adjustedP, double foldEnrichment, List<String> genes) {
            this.term = term;
            this.hitCount = hitCount;
            this.termSize = termSize;
            this.backgroundSize = backgroundSize;
            this.targetSize = targetSize;
            this.pValue = pValue;
            this.adjustedP = adjustedP;
            this.foldEnrichment = foldEnrichment;
            this.genes = List.copyOf(genes);
        }

        public String term() { return term; }
        public int hitCount() { return hitCount; }
        public int termSize() { return termSize; }
        public int backgroundSize() { return backgroundSize; }
        public int targetSize() { return targetSize; }
        public double pValue() { return pValue; }
        public double adjustedP() { return adjustedP; }
        public double foldEnrichment() { return foldEnrichment; }
        public List<String> genes() { return genes; }
    }
}
