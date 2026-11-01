package io.opentools.analysis.expression;

import java.util.List;

public record ExpressionMatrix(
        List<String> rowNames,
        List<String> columnNames,
        List<List<Double>> values
) {
    public ExpressionMatrix {
        rowNames = rowNames == null ? List.of() : List.copyOf(rowNames);
        columnNames = columnNames == null ? List.of() : List.copyOf(columnNames);
        values = values == null ? List.of() : values.stream().map(List::copyOf).toList();
        if (rowNames.size() != values.size()) {
            throw new IllegalArgumentException("Expression row names and matrix row count differ");
        }
        for (List<Double> row : values) {
            if (row.size() != columnNames.size()) {
                throw new IllegalArgumentException("Expression matrix is ragged");
            }
        }
    }

    public double value(int row, int column) {
        return values.get(row).get(column);
    }
}
