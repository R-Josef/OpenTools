package io.opentools.external.alignment;

import io.opentools.api.tool.ResultTable;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AlignmentTableParser {
    public AlignmentTableSummary parse(Path output) throws Exception {
        long totalHits = 0;
        Map<String, QuerySummary> queries = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(output, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\t", -1);
                if (columns.length < 12) {
                    continue;
                }
                BlastHit hit = new BlastHit(
                        columns[0],
                        columns[1],
                        number(columns[2]),
                        integer(columns[3]),
                        number(columns[10]),
                        number(columns[11])
                );
                totalHits++;
                QuerySummary summary = queries.computeIfAbsent(hit.query(), ignored -> new QuerySummary());
                summary.totalHits++;
                if (summary.best == null || isBetter(hit, summary.best)) {
                    summary.best = hit;
                }
            }
        }
        List<BlastHit> bestHits = queries.values().stream()
                .map(summary -> summary.best)
                .toList();
        List<List<String>> rows = bestHits.stream()
                .map(hit -> List.of(
                        hit.query(),
                        Long.toString(queries.get(hit.query()).totalHits),
                        hit.subject(),
                        format(hit.percentIdentity()),
                        Long.toString(hit.alignmentLength()),
                        format(hit.evalue()),
                        format(hit.bitscore())
                ))
                .toList();
        ResultTable table = new ResultTable(
                List.of("Query", "Hits", "Best subject", "% identity", "Alignment length", "E-value", "Bitscore"),
                rows
        );
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalHits", totalHits);
        metrics.put("queries", (long) queries.size());
        metrics.put("queriesWithHits", (long) queries.size());
        metrics.put("bestEvalue", bestHits.stream().mapToDouble(BlastHit::evalue).min().orElse(Double.NaN));
        return new AlignmentTableSummary(totalHits, queries.size(), bestHits, table, metrics);
    }

    private static boolean isBetter(BlastHit candidate, BlastHit current) {
        int evalue = Double.compare(candidate.evalue(), current.evalue());
        if (evalue != 0) {
            return evalue < 0;
        }
        return candidate.bitscore() > current.bitscore();
    }

    private static long integer(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static double number(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    private static String format(double value) {
        return Double.isNaN(value) ? "" : "%.4g".formatted(value);
    }

    private static final class QuerySummary {
        private long totalHits;
        private BlastHit best;

        private QuerySummary() {
        }
    }
}
