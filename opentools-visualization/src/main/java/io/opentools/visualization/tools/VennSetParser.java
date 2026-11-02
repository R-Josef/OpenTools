package io.opentools.visualization.tools;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenToolException;
import io.opentools.formats.Compression;
import io.opentools.visualization.model.VennData;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class VennSetParser {
    ParseResult parse(Path input, String requestedDelimiter, CancellationToken cancellationToken) throws Exception {
        char delimiter = detectDelimiter(input, requestedDelimiter);
        List<LinkedHashSet<String>> sets = new ArrayList<>();
        List<String> setNames = new ArrayList<>();
        long rows = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(input),
                StandardCharsets.UTF_8
        ))) {
            String line = reader.readLine();
            if (line == null) {
                throw error("VIS_VENN_EMPTY", "The set table is empty.", "Provide a header row with at least two set columns.");
            }
            if (!line.isEmpty() && line.charAt(0) == '\ufeff') {
                line = line.substring(1);
            }
            String[] header = parseLine(line, delimiter);
            for (int index = 0; index < header.length; index++) {
                String name = header[index].trim();
                if (!name.isEmpty()) {
                    setNames.add(name);
                    sets.add(new LinkedHashSet<>());
                }
            }
            if (setNames.size() < 2) {
                throw error(
                        "VIS_VENN_SET_COUNT",
                        "At least two non-empty set columns are required.",
                        "Add set names to the first row."
                );
            }
            while ((line = reader.readLine()) != null) {
                cancellationToken.throwIfCancelled();
                if (line.isBlank()) {
                    continue;
                }
                rows++;
                String[] cells = parseLine(line, delimiter);
                for (int column = 0; column < setNames.size() && column < cells.length; column++) {
                    String value = cells[column].trim();
                    if (!value.isEmpty() && !value.equalsIgnoreCase("NA") && !value.equalsIgnoreCase("NULL")) {
                        sets.get(column).add(value);
                    }
                }
            }
        }
        if (sets.stream().allMatch(Set::isEmpty)) {
            throw error("VIS_VENN_EMPTY", "No set members were found.", "Add one identifier per row in each set column.");
        }

        Map<String, MutableIntersection> intersections = new LinkedHashMap<>();
        for (int setIndex = 0; setIndex < sets.size(); setIndex++) {
            String setName = setNames.get(setIndex);
            for (String element : sets.get(setIndex)) {
                intersections.computeIfAbsent(element, ignored -> new MutableIntersection()).members.add(setName);
            }
        }
        Map<String, CountAndExamples> grouped = new LinkedHashMap<>();
        for (Map.Entry<String, MutableIntersection> entry : intersections.entrySet()) {
            List<String> members = entry.getValue().members.stream().sorted().toList();
            String key = String.join("\u0000", members);
            CountAndExamples value = grouped.computeIfAbsent(key, ignored -> new CountAndExamples(members));
            value.count++;
            if (value.examples.size() < 10) {
                value.examples.add(entry.getKey());
            }
        }
        List<VennData.Intersection> result = grouped.values().stream()
                .map(value -> new VennData.Intersection(value.sets, value.count, value.examples))
                .sorted(Comparator.comparingInt(VennData.Intersection::count).reversed()
                        .thenComparing(intersection -> String.join("\u0000", intersection.sets())))
                .toList();
        List<Integer> sizes = sets.stream().map(Set::size).toList();
        return new ParseResult(setNames, sizes, result, delimiter, rows);
    }

    private static char detectDelimiter(Path input, String selected) throws Exception {
        if (selected != null && !selected.equals("AUTO")) {
            return switch (selected) {
                case "COMMA" -> ',';
                case "SEMICOLON" -> ';';
                default -> '\t';
            };
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(input),
                StandardCharsets.UTF_8
        ))) {
            String line;
            while ((line = reader.readLine()) != null && line.isBlank()) {
            }
            if (line == null) {
                throw error("VIS_VENN_EMPTY", "The set table is empty.", "Provide a header row with set names.");
            }
            long tabs = line.chars().filter(character -> character == '\t').count();
            long commas = line.chars().filter(character -> character == ',').count();
            long semicolons = line.chars().filter(character -> character == ';').count();
            if (tabs >= commas && tabs >= semicolons) {
                return '\t';
            }
            return commas >= semicolons ? ',' : ';';
        }
    }

    private static String[] parseLine(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == delimiter && !quoted) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        cells.add(current.toString());
        return cells.toArray(String[]::new);
    }

    private static OpenToolException error(String code, String message, String suggestion) {
        return new OpenToolException(code, message, suggestion);
    }

    record ParseResult(
            List<String> setNames,
            List<Integer> setSizes,
            List<VennData.Intersection> intersections,
            char delimiter,
            long tableRows
    ) {
    }

    private static final class MutableIntersection {
        private final Set<String> members = new LinkedHashSet<>();
    }

    private static final class CountAndExamples {
        private final List<String> sets;
        private final List<String> examples = new ArrayList<>();
        private int count;

        private CountAndExamples(List<String> sets) {
            this.sets = List.copyOf(sets);
        }
    }
}
