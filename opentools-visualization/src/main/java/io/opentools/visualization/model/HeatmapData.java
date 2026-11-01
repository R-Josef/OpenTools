package io.opentools.visualization.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record HeatmapData(
        List<String> rowLabels,
        List<String> columnLabels,
        List<List<Double>> values,
        String rowOrder,
        String columnOrder,
        int sourceRows,
        int sourceColumns,
        long missingValues,
        long invalidValues,
        long clippedValues,
        int rowSampleStep,
        int columnSampleStep,
        List<PlotDiagnostic> diagnostics
) implements PlotData {
    public HeatmapData {
        rowLabels = immutable(rowLabels);
        columnLabels = immutable(columnLabels);
        if (values == null || values.size() != rowLabels.size()) {
            throw new IllegalArgumentException("Heatmap matrix row count must match row labels");
        }
        List<List<Double>> copy = new ArrayList<>();
        for (List<Double> row : values) {
            if (row == null || row.size() != columnLabels.size()) {
                throw new IllegalArgumentException("Every heatmap row must match the column-label count");
            }
            copy.add(Collections.unmodifiableList(new ArrayList<>(row)));
        }
        values = Collections.unmodifiableList(copy);
        rowOrder = rowOrder == null ? "ORIGINAL" : rowOrder;
        columnOrder = columnOrder == null ? "ORIGINAL" : columnOrder;
        sourceRows = Math.max(sourceRows, rowLabels.size());
        sourceColumns = Math.max(sourceColumns, columnLabels.size());
        rowSampleStep = Math.max(1, rowSampleStep);
        columnSampleStep = Math.max(1, columnSampleStep);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        if (rowLabels.isEmpty() || columnLabels.isEmpty()) {
            throw new IllegalArgumentException("A heatmap requires at least one row and one column");
        }
    }

    public int missingCount() {
        return (int) values.stream().flatMap(List::stream).filter(value -> value == null).count();
    }

    private static List<String> immutable(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
