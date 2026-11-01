package io.opentools.visualization.task;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenToolException;
import io.opentools.formats.Compression;
import io.opentools.visualization.model.HeatmapData;
import io.opentools.visualization.model.PlotDiagnostic;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

final class MatrixParser {
    private static final int MAX_DIAGNOSTICS = 200;

    MatrixResult parse(Config config, CancellationToken cancellationToken) throws Exception {
        return parse(config, cancellationToken, 1);
    }

    MatrixResult parse(
            Config config,
            CancellationToken cancellationToken,
            int threads
    ) throws Exception {
        char delimiter = detectDelimiter(config.input(), config.delimiter());
        List<PlotDiagnostic> diagnostics = new ArrayList<>();
        List<Integer> selectedColumns;
        List<String> columnLabels;
        Map<Integer, String> headerBySource = new LinkedHashMap<>();
        int sourceColumns;
        long missing = 0;
        long invalid = 0;
        int sourceRows = 0;
        long lineNumber = 0;
        List<RawRow> reservoir = new ArrayList<>();
        Random random = new Random(97_531L);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Compression.open(config.input()),
                StandardCharsets.UTF_8
        ))) {
            String line;
            String[] header = null;
            while ((line = reader.readLine()) != null) {
                cancellationToken.throwIfCancelled();
                lineNumber++;
                if (!line.isEmpty() && line.charAt(0) == '\ufeff') {
                    line = line.substring(1);
                }
                if (line.isBlank()) {
                    continue;
                }
                header = parseLine(line, delimiter);
                break;
            }
            if (header == null) {
                throw error("VIS_MATRIX_EMPTY", "The matrix is empty.", "Choose a non-empty delimited matrix.");
            }
            if (header.length < 2) {
                throw error(
                        "VIS_MATRIX_NO_COLUMNS",
                        "The matrix header must contain a row-name column and at least one data column.",
                        "Use a header row such as: row<TAB>sample_1<TAB>sample_2."
                );
            }
            sourceColumns = header.length - 1;
            List<Integer> sampledSourceColumns = sampleIndices(sourceColumns, config.maxColumns());
            selectedColumns = sampledSourceColumns.stream().map(index -> index + 1).toList();
            columnLabels = uniqueLabels(header, selectedColumns, "column");
            for (int column = 1; column < header.length; column++) {
                headerBySource.put(column, clean(header[column]));
            }

            while ((line = reader.readLine()) != null) {
                cancellationToken.throwIfCancelled();
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                sourceRows++;
                String[] cells = parseLine(line, delimiter);
                String rowLabel = clean(cells[0]);
                if (rowLabel.isBlank()) {
                    rowLabel = "row_" + sourceRows;
                }
                if (cells.length - 1 > sourceColumns && diagnostics.size() < MAX_DIAGNOSTICS) {
                    diagnostics.add(new PlotDiagnostic(
                            "WARNING",
                            "visual.diagnostic.extra_columns",
                            "Row " + rowLabel + " has " + (cells.length - 1)
                                    + " values but the header defines " + sourceColumns + ".",
                            lineNumber,
                            sourceRows,
                            0
                    ));
                }

                for (int sourceColumn = 1; sourceColumn <= sourceColumns; sourceColumn++) {
                    if (sourceColumn >= cells.length) {
                        missing++;
                        if (diagnostics.size() < MAX_DIAGNOSTICS) {
                            diagnostics.add(missingDiagnostic(
                                    lineNumber,
                                    sourceRows,
                                    sourceColumn,
                                    headerBySource.getOrDefault(sourceColumn, "column_" + sourceColumn),
                                    "Missing value"
                            ));
                        }
                        continue;
                    }
                    String raw = clean(cells[sourceColumn]);
                    if (raw.isEmpty() || isMissingToken(raw)) {
                        missing++;
                        if (diagnostics.size() < MAX_DIAGNOSTICS) {
                            diagnostics.add(missingDiagnostic(
                                    lineNumber,
                                    sourceRows,
                                    sourceColumn,
                                    headerBySource.getOrDefault(sourceColumn, "column_" + sourceColumn),
                                    "Missing value"
                            ));
                        }
                    } else if (!isFiniteNumber(raw)) {
                        invalid++;
                        if (config.invalidPolicy().equals("ERROR")) {
                            throw error(
                                    "VIS_MATRIX_INVALID_VALUE",
                                    "Invalid numeric value '" + raw + "' at line " + lineNumber
                                            + ", column " + sourceColumn + ".",
                                    "Replace the value with a decimal number, or set invalid values to missing."
                            );
                        }
                        if (diagnostics.size() < MAX_DIAGNOSTICS) {
                            diagnostics.add(new PlotDiagnostic(
                                    "WARNING",
                                    "visual.diagnostic.invalid_as_missing",
                                    "Invalid value '" + raw + "' at line " + lineNumber
                                            + " was treated as missing.",
                                    lineNumber,
                                    sourceRows,
                                    sourceColumn
                            ));
                        }
                    }
                }

                RawRow rawRow = new RawRow(
                        rowLabel,
                        sourceRows,
                        lineNumber,
                        selectedColumns,
                        cells,
                        headerBySource,
                        config
                );
                if (config.missingPolicy().equals("ERROR") && rawRow.hasMissing()) {
                    throw error(
                            "VIS_MATRIX_MISSING_VALUE",
                            "Missing value at line " + lineNumber + " in row '" + rowLabel + "'.",
                            "Fill the value or choose a missing-value policy."
                    );
                }
                rawRow.materialize(config.missingPolicy());
                if (reservoir.size() < config.maxRows()) {
                    reservoir.add(rawRow);
                } else {
                    int replacement = random.nextInt(sourceRows);
                    if (replacement < config.maxRows()) {
                        reservoir.set(replacement, rawRow);
                    }
                }
            }
        }

        if (sourceRows == 0) {
            throw error("VIS_MATRIX_NO_ROWS", "The matrix has no data rows.", "Add at least one row below the header.");
        }
        List<RawRow> ordered = orderRows(
                reservoir,
                config.rowOrder(),
                config.maxClusterItems(),
                cancellationToken,
                threads
        );
        List<Integer> order = columnOrder(
                ordered,
                config.columnOrder(),
                config.maxClusterItems(),
                cancellationToken,
                threads
        );
        List<String> orderedColumnLabels = new ArrayList<>();
        for (int index : order) {
            orderedColumnLabels.add(columnLabels.get(index));
        }

        List<String> rowLabels = new ArrayList<>();
        List<List<Double>> values = new ArrayList<>();
        for (RawRow row : ordered) {
            rowLabels.add(row.label);
            List<Double> selected = new ArrayList<>();
            for (int index : order) {
                selected.add(row.values.get(index));
            }
            if (config.normalization().equals("ROW_ZSCORE")) {
                selected = rowZScore(selected);
            }
            values.add(selected);
        }
        rowLabels = uniqueRowLabels(rowLabels);

        int rowStep = Math.max(1, (int) Math.ceil(sourceRows / (double) rowLabels.size()));
        int columnStep = selectedColumns.size() <= 1
                ? 1
                : Math.max(1, (int) Math.ceil(sourceColumns / (double) selectedColumns.size()));
        HeatmapData data = new HeatmapData(
                rowLabels,
                orderedColumnLabels,
                values,
                config.rowOrder(),
                config.columnOrder(),
                sourceRows,
                sourceColumns,
                missing,
                invalid,
                0,
                rowStep,
                columnStep,
                diagnostics
        );
        return new MatrixResult(data, delimiter, minimum(data), maximum(data));
    }

    private static List<RawRow> orderRows(
            List<RawRow> rows,
            String order,
            int maxClusterItems,
            CancellationToken cancellationToken,
            int threads
    ) {
        Comparator<RawRow> label = Comparator.<RawRow, String>comparing(
                        row -> row.label,
                        String.CASE_INSENSITIVE_ORDER
                )
                .thenComparing(row -> row.label);
        Comparator<RawRow> mean = Comparator.comparingDouble(RawRow::mean)
                .thenComparing((row -> row.label), String.CASE_INSENSITIVE_ORDER);
        return switch (order) {
            case "NAME_ASC" -> rows.stream().sorted(label).toList();
            case "NAME_DESC" -> rows.stream().sorted(label.reversed()).toList();
            case "MEAN_ASC" -> rows.stream().sorted(mean).toList();
            case "MEAN_DESC" -> rows.stream().sorted(mean.reversed()).toList();
            case "HIERARCHICAL" -> {
                List<Integer> clusterOrder = HierarchicalClusterer.order(
                        rows.stream().map(row -> row.values).toList(),
                        rows.stream().map(row -> row.label).toList(),
                        maxClusterItems,
                        cancellationToken,
                        threads
                );
                yield clusterOrder.stream().map(rows::get).toList();
            }
            default -> List.copyOf(rows);
        };
    }

    private static List<Integer> columnOrder(
            List<RawRow> rows,
            String order,
            int maxClusterItems,
            CancellationToken cancellationToken,
            int threads
    ) {
        int columns = rows.getFirst().values.size();
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < columns; index++) {
            indices.add(index);
        }
        Comparator<Integer> mean = Comparator.<Integer>comparingDouble(index -> {
            double total = 0;
            int count = 0;
            for (RawRow row : rows) {
                Double value = row.values.get(index);
                if (value != null) {
                    total += value;
                    count++;
                }
            }
            return count == 0 ? Double.NEGATIVE_INFINITY : total / count;
        }).thenComparingInt(Integer::intValue);
        return switch (order) {
            case "MEAN_ASC" -> indices.stream().sorted(mean).toList();
            case "MEAN_DESC" -> indices.stream().sorted(mean.reversed()).toList();
            case "NAME_ASC", "NAME_DESC" -> {
                List<String> labels = rows.getFirst().columnLabels;
                Comparator<Integer> named = Comparator.<Integer, String>comparing(
                        index -> labels.get(index),
                        String.CASE_INSENSITIVE_ORDER
                ).thenComparingInt(Integer::intValue);
                yield indices.stream().sorted(order.equals("NAME_DESC") ? named.reversed() : named).toList();
            }
            case "HIERARCHICAL" -> {
                List<List<Double>> vectors = new ArrayList<>(columns);
                for (int column = 0; column < columns; column++) {
                    List<Double> vector = new ArrayList<>(rows.size());
                    for (RawRow row : rows) {
                        vector.add(row.values.get(column));
                    }
                    vectors.add(vector);
                }
                yield HierarchicalClusterer.order(
                        vectors,
                        rows.getFirst().columnLabels,
                        maxClusterItems,
                        cancellationToken,
                        threads
                );
            }
            default -> indices;
        };
    }

    private static List<Double> rowZScore(List<Double> values) {
        double sum = 0;
        int count = 0;
        for (Double value : values) {
            if (value != null) {
                sum += value;
                count++;
            }
        }
        if (count == 0) {
            return values;
        }
        double mean = sum / count;
        double variance = 0;
        for (Double value : values) {
            if (value != null) {
                double difference = value - mean;
                variance += difference * difference;
            }
        }
        double standardDeviation = count <= 1 ? 0 : Math.sqrt(variance / (count - 1));
        List<Double> transformed = new ArrayList<>(values.size());
        for (Double value : values) {
            if (value == null) {
                transformed.add(null);
            } else {
                transformed.add(standardDeviation == 0 ? 0.0 : (value - mean) / standardDeviation);
            }
        }
        return transformed;
    }

    private static List<Integer> sampleIndices(int count, int maximum) {
        if (count <= maximum) {
            List<Integer> all = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                all.add(index);
            }
            return all;
        }
        if (maximum == 1) {
            return List.of(0);
        }
        List<Integer> sampled = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int index = 0; index < maximum; index++) {
            int source = (int) Math.round(index * (count - 1.0) / (maximum - 1.0));
            if (seen.add(source)) {
                sampled.add(source);
            }
        }
        return sampled;
    }

    private static List<String> uniqueLabels(String[] header, List<Integer> columns, String prefix) {
        List<String> labels = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (int column : columns) {
            String base = clean(header[column]);
            if (base.isBlank()) {
                base = prefix + "_" + column;
            }
            String label = base;
            int suffix = 2;
            while (!used.add(label.toLowerCase(Locale.ROOT))) {
                label = base + "_" + suffix++;
            }
            labels.add(label);
        }
        return labels;
    }

    private static List<String> uniqueRowLabels(List<String> labels) {
        List<String> unique = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (String label : labels) {
            String value = label;
            int suffix = 2;
            while (!used.add(value.toLowerCase(Locale.ROOT))) {
                value = label + "_" + suffix++;
            }
            unique.add(value);
        }
        return unique;
    }

    private static char detectDelimiter(Path input, String selected) throws Exception {
        if (!selected.equals("AUTO")) {
            return switch (selected) {
                case "TAB" -> '\t';
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
            while ((line = reader.readLine()) != null && !line.isBlank()) {
                long tabs = line.chars().filter(character -> character == '\t').count();
                long commas = line.chars().filter(character -> character == ',').count();
                long semicolons = line.chars().filter(character -> character == ';').count();
                if (tabs >= commas && tabs >= semicolons) {
                    return '\t';
                }
                return commas >= semicolons ? ',' : ';';
            }
        }
        throw error("VIS_MATRIX_EMPTY", "The matrix is empty.", "Choose a non-empty delimited matrix.");
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

    private static boolean isFiniteNumber(String value) {
        try {
            return Double.isFinite(Double.parseDouble(value));
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean isMissingToken(String value) {
        return value.equalsIgnoreCase("NA")
                || value.equalsIgnoreCase("N/A")
                || value.equalsIgnoreCase("NULL")
                || value.equalsIgnoreCase("NONE")
                || value.equals("-");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static PlotDiagnostic missingDiagnostic(long line, int row, int column, String columnName, String label) {
        return new PlotDiagnostic(
                "WARNING",
                "visual.diagnostic.missing_value",
                label + " '" + columnName + "' at line " + line + ".",
                line,
                row,
                column
        );
    }

    private static OpenToolException error(String code, String message, String suggestion) {
        return new OpenToolException(code, message, suggestion);
    }

    private static double minimum(HeatmapData data) {
        return data.values().stream().flatMap(List::stream)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .min().orElse(0);
    }

    private static double maximum(HeatmapData data) {
        return data.values().stream().flatMap(List::stream)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max().orElse(1);
    }

    record Config(
            Path input,
            String delimiter,
            String missingPolicy,
            String invalidPolicy,
            String rowOrder,
            String columnOrder,
            String normalization,
            int maxRows,
            int maxColumns,
            int maxClusterItems
    ) {
        Config(
                Path input,
                String delimiter,
                String missingPolicy,
                String invalidPolicy,
                String rowOrder,
                String columnOrder,
                String normalization,
                int maxRows,
                int maxColumns
        ) {
            this(
                    input,
                    delimiter,
                    missingPolicy,
                    invalidPolicy,
                    rowOrder,
                    columnOrder,
                    normalization,
                    maxRows,
                    maxColumns,
                    1000
            );
        }
    }

    record MatrixResult(HeatmapData data, char delimiter, double minimum, double maximum) {
    }

    private static final class RawRow {
        private final String label;
        private final int sourceRow;
        private final long line;
        private final List<Integer> columns;
        private final String[] cells;
        private final Map<Integer, String> headerBySource;
        private final Config config;
        private final List<String> columnLabels;
        private final List<Double> values = new ArrayList<>();
        private final List<Boolean> missingFlags = new ArrayList<>();

        private RawRow(
                String label,
                int sourceRow,
                long line,
                List<Integer> columns,
                String[] cells,
                Map<Integer, String> headerBySource,
                Config config
        ) {
            this.label = label;
            this.sourceRow = sourceRow;
            this.line = line;
            this.columns = columns;
            this.cells = cells;
            this.headerBySource = headerBySource;
            this.config = config;
            this.columnLabels = new ArrayList<>();
            for (int column : columns) {
                columnLabels.add(headerBySource.getOrDefault(column, "column_" + column));
            }
        }

        private boolean hasMissing() {
            for (int column : columns) {
                if (column >= cells.length || clean(cells[column]).isEmpty()
                        || (!config.invalidPolicy().equals("ERROR") && !isFiniteNumber(clean(cells[column])))) {
                    return true;
                }
            }
            return false;
        }

        private void materialize(String missingPolicy) {
            for (int column : columns) {
                String raw = column < cells.length ? clean(cells[column]) : "";
                boolean isMissing = raw.isEmpty() || isMissingToken(raw) || !isFiniteNumber(raw);
                missingFlags.add(isMissing);
                if (isMissing) {
                    values.add(missingPolicy.equals("ZERO") ? 0.0 : null);
                } else {
                    values.add(Double.parseDouble(raw));
                }
            }
            if (missingPolicy.equals("ROW_MEAN")) {
                double sum = 0;
                int count = 0;
                for (Double value : values) {
                    if (value != null) {
                        sum += value;
                        count++;
                    }
                }
                double mean = count == 0 ? 0 : sum / count;
                for (int index = 0; index < values.size(); index++) {
                    if (values.get(index) == null) {
                        values.set(index, mean);
                    }
                }
            }
        }

        private double mean() {
            double total = 0;
            int count = 0;
            for (Double value : values) {
                if (value != null) {
                    total += value;
                    count++;
                }
            }
            return count == 0 ? Double.NEGATIVE_INFINITY : total / count;
        }
    }
}
