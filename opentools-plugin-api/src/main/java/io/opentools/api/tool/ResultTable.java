package io.opentools.api.tool;

import java.util.List;

/**
 * Immutable tabular result with column names and string rows.
 *
 * @param columns column headers
 * @param rows table rows
 */
public record ResultTable(List<String> columns, List<List<String>> rows) {
    public ResultTable {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : rows.stream().map(List::copyOf).toList();
    }

    /**
     * Returns an empty result table.
     *
     * @return table without columns or rows
     */
    public static ResultTable empty() {
        return new ResultTable(List.of(), List.of());
    }
}
