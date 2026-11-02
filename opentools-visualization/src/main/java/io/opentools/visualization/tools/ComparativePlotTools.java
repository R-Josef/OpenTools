package io.opentools.visualization.tools;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.ToolExecutionContext;
import io.opentools.api.tool.ToolResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.visualization.model.GenericPlotData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ComparativePlotTools {
    private ComparativePlotTools() {
    }

    public static List<AbstractOpenTool> tools() {
        return List.of(new PlotCollinearityTool(), new PlotDotplotTool());
    }
}

final class PlotCollinearityTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.collinearity",
            "Collinearity Plot",
            "Plot imported MCScanX collinearity pairs grouped by block.",
            List.of(
                    ToolParameter.requiredFile("input", "Collinearity table", "Output from mcscan.import."),
                    ToolParameter.string("blockColumn", "Block column", "Block identifier column.", "block"),
                    ToolParameter.string("pairColumn", "Pair column", "Pair order column.", "pair_index")
            ),
            "Collinearity"
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) { return PlotToolSupport.validate(request); }

    @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        var table = PlotToolSupport.readTable(request.path("input"), null);
        int block = table.requiredColumn(request.string("blockColumn"));
        int pair = table.requiredColumn(request.string("pairColumn"));
        Map<String, GenericPlotData.GenericSeries.Builder> groups = new LinkedHashMap<>();
        for (var row : table.rows()) {
            String name = PlotToolSupport.cell(row, block);
            double value = PlotToolSupport.number(row, pair);
            groups.computeIfAbsent(name, GenericPlotData.GenericSeries.Builder::new).add(value, value);
        }
        GenericPlotData data = new GenericPlotData("SCATTER", "Collinear pair order",
                "Collinear pair order", List.of(), groups.values().stream()
                .map(GenericPlotData.GenericSeries.Builder::build).toList());
        return PlotToolSupport.renderGeneric(this, request, context, data,
                "collinearity", request.string("title"), "result.plot.collinearity.summary", groups.size());
    }
}

final class PlotDotplotTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.dotplot",
            "Dot Plot",
            "Plot two numeric coordinate columns with optional series grouping.",
            List.of(
                    ToolParameter.requiredFile("input", "Dot plot table", "Delimited table with x and y columns."),
                    ToolParameter.string("xColumn", "X column", "Numeric x column.", "x"),
                    ToolParameter.string("yColumn", "Y column", "Numeric y column.", "y"),
                    ToolParameter.string("groupColumn", "Group column", "Optional series grouping column.", "")
            ),
            "Dot Plot"
    );

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ValidationResult validate(ToolRequest request) { return PlotToolSupport.validate(request); }

    @Override public ToolResult execute(ToolRequest request, ToolExecutionContext context) throws Exception {
        var table = PlotToolSupport.readTable(request.path("input"), null);
        int x = table.requiredColumn(request.string("xColumn"));
        int y = table.requiredColumn(request.string("yColumn"));
        int group = request.string("groupColumn").isBlank() ? -1 : table.column(request.string("groupColumn"));
        Map<String, GenericPlotData.GenericSeries.Builder> groups = new LinkedHashMap<>();
        for (var row : table.rows()) {
            String name = group >= 0 ? PlotToolSupport.cell(row, group) : "All";
            if (name.isBlank()) {
                name = "All";
            }
            groups.computeIfAbsent(name, GenericPlotData.GenericSeries.Builder::new)
                    .add(PlotToolSupport.number(row, x), PlotToolSupport.number(row, y));
        }
        GenericPlotData data = new GenericPlotData("SCATTER", request.string("xColumn"),
                request.string("yColumn"), List.of(), groups.values().stream()
                .map(GenericPlotData.GenericSeries.Builder::build).toList());
        return PlotToolSupport.renderGeneric(this, request, context, data,
                "dotplot", request.string("title"), "result.plot.dotplot.summary", groups.size());
    }
}
