package io.opentools.visualization.tools;

import io.opentools.api.tool.ParameterType;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

final class EnrichmentPlotSupport {
    private EnrichmentPlotSupport() {
    }

    static ToolParameter selectionParameter() {
        return new ToolParameter(
                "selectionMode",
                "Term selection",
                "Choose most significant adjusted P values or the first rows in input order.",
                ParameterType.ENUM,
                false,
                "TOP_SIGNIFICANT",
                List.of("TOP_SIGNIFICANT", "INPUT_ORDER"),
                null,
                null,
                false,
                null
        );
    }

    static List<SelectedRow> selectRows(
            PlotToolSupport.PlotTable table,
            int adjustedPColumn,
            ToolRequest request
    ) {
        List<SelectedRow> rows = IntStream.range(0, table.rows().size())
                .mapToObj(index -> new SelectedRow(
                        index,
                        table.rows().get(index),
                        Math.max(0, PlotToolSupport.number(
                                table.rows().get(index), adjustedPColumn))
                ))
                .toList();
        if ("INPUT_ORDER".equals(request.string("selectionMode"))) {
            return rows.stream().limit(request.integer("maxTerms")).toList();
        }
        return rows.stream()
                .sorted(Comparator.comparingDouble(SelectedRow::adjustedP)
                        .thenComparingInt(SelectedRow::index))
                .limit(request.integer("maxTerms"))
                .toList();
    }

    static ToolResult withSelectionMetrics(
            ToolResult result,
            int inputRows,
            int selectedRows,
            String selectionMode
    ) {
        Map<String, Object> metrics = new LinkedHashMap<>(result.metrics());
        metrics.put("selectionMode", selectionMode);
        metrics.put("inputRows", inputRows);
        metrics.put("selectedRows", selectedRows);
        return new ToolResult(
                result.summary(),
                result.summaryKey(),
                result.summaryArguments(),
                result.detail(),
                result.detailKey(),
                result.detailArguments(),
                result.table(),
                result.artifacts(),
                metrics
        );
    }

    record SelectedRow(int index, List<String> row, double adjustedP) {
    }
}
