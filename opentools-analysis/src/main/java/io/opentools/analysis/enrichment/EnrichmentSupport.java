package io.opentools.analysis.enrichment;

import io.opentools.api.tool.OpenToolException;
import io.opentools.formats.Compression;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class EnrichmentSupport {
    private EnrichmentSupport() {
    }

    static Map<String, Set<String>> readAnnotations(Path path) throws Exception {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\\t", -1);
                if (fields.length < 2 || fields[0].equalsIgnoreCase("gene") || fields[0].equalsIgnoreCase("id")) {
                    continue;
                }
                Set<String> terms = result.computeIfAbsent(fields[0].trim(), ignored -> new LinkedHashSet<>());
                for (String term : fields[1].split("[,;| ]+")) {
                    if (!term.isBlank() && !term.equals("-")) {
                        terms.add(term);
                    }
                }
            }
        }
        if (result.isEmpty()) {
            throw new OpenToolException("ANNOTATION_EMPTY",
                    "No gene-to-term annotations were found.",
                    "Provide a two-column gene/term table.");
        }
        return result;
    }

    static Set<String> readGeneList(Path path) throws Exception {
        Set<String> genes = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.trim();
                if (!value.isEmpty() && !value.startsWith("#") && !value.equalsIgnoreCase("gene")
                        && !value.equalsIgnoreCase("id")) {
                    genes.add(value.split("\\t", 2)[0]);
                }
            }
        }
        return genes;
    }

    static List<EnrichmentEngine.Result> pruneRedundant(
            List<EnrichmentEngine.Result> results,
            double jaccardThreshold
    ) {
        if (jaccardThreshold <= 0) {
            return results;
        }
        List<EnrichmentEngine.Result> kept = new ArrayList<>();
        for (EnrichmentEngine.Result candidate : results) {
            boolean redundant = false;
            for (EnrichmentEngine.Result accepted : kept) {
                Set<String> intersection = new LinkedHashSet<>(candidate.genes());
                intersection.retainAll(accepted.genes());
                Set<String> union = new LinkedHashSet<>(candidate.genes());
                union.addAll(accepted.genes());
                double jaccard = union.isEmpty() ? 1 : intersection.size() / (double) union.size();
                if (jaccard >= jaccardThreshold) {
                    redundant = true;
                    break;
                }
            }
            if (!redundant) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    static void writeResults(
            BufferedWriter writer,
            List<EnrichmentEngine.Result> results,
            String countLabel
    ) throws Exception {
        writer.write("term\t" + countLabel + "\tterm_size\tbackground_size\ttarget_size"
                + "\tp_value\tadjusted_p_value\tfold_enrichment\tgenes\n");
        for (EnrichmentEngine.Result result : results) {
            writer.write(result.term());
            writer.write('\t');
            writer.write(Integer.toString(result.hitCount()));
            writer.write('\t');
            writer.write(Integer.toString(result.termSize()));
            writer.write('\t');
            writer.write(Integer.toString(result.backgroundSize()));
            writer.write('\t');
            writer.write(Integer.toString(result.targetSize()));
            writer.write('\t');
            writer.write(format(result.pValue()));
            writer.write('\t');
            writer.write(format(result.adjustedP()));
            writer.write('\t');
            writer.write(format(result.foldEnrichment()));
            writer.write('\t');
            writer.write(String.join(",", result.genes()));
            writer.newLine();
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.10g", value);
    }
}
