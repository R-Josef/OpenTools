package io.opentools.visualization.task;

import io.opentools.api.tool.AbstractOpenTool;
import io.opentools.api.tool.TaskContext;
import io.opentools.api.tool.TaskResult;
import io.opentools.api.tool.ToolDescriptor;
import io.opentools.api.tool.ToolParameter;
import io.opentools.api.tool.ToolRequest;
import io.opentools.api.tool.ValidationResult;
import io.opentools.visualization.model.GenericPlotData;

import java.util.ArrayList;
import java.util.List;

public final class EnrichmentPlotTools {
    private EnrichmentPlotTools() {
    }

    public static List<AbstractOpenTool> tools() {
        return List.of(new PlotEnrichmentBarTool(), new PlotEnrichmentBubbleTool());
    }
}

final class PlotEnrichmentBarTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.enrichment-bar",
            "Enrichment Bar Plot",
            "Plot enrichment terms as -log10 adjusted P-value bars.",
            List.of(
                    ToolParameter.requiredFile("input", "Enrichment table", "Output from GO/KEGG enrichment."),
                    ToolParameter.string("termColumn", "Term column", "Term identifier column.", "term"),
                    ToolParameter.string("adjustedPColumn", "Adjusted P column", "Adjusted P-value column.", "adjusted_p_value"),
                    ToolParameter.integer("maxTerms", "Maximum terms", "Maximum terms plotted.", 20, 1, 200),
                    EnrichmentPlotSupport.selectionParameter()
            ),
            "Enrichment"
    );

    @Override
    public ToolDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public ValidationResult validate(ToolRequest request) { return PlotToolSupport.validate(request); }

    @Override
    public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        var table = PlotToolSupport.readTable(request.path("input"), null);
        int term = table.requiredColumn(request.string("termColumn"));
        int p = table.requiredColumn(request.string("adjustedPColumn"));
        List<String> categories = new ArrayList<>();
        GenericPlotData.GenericSeries.Builder series = new GenericPlotData.GenericSeries.Builder("Significance");
        List<EnrichmentPlotSupport.SelectedRow> selected = EnrichmentPlotSupport.selectRows(table, p, request);
        selected.forEach(selectedRow -> {
            List<String> row = selectedRow.row();
            categories.add(PlotToolSupport.cell(row, term));
            double value = Math.max(0, selectedRow.adjustedP());
            series.add(categories.size() - 1, value <= 0 ? 300 : -Math.log10(value));
        });
        GenericPlotData data = new GenericPlotData("BAR", "Term", "-log10 adjusted P",
                categories, List.of(series.build()));
        TaskResult result = PlotToolSupport.renderGeneric(this, request, context, data,
                "enrichment-bar", request.string("title"), "result.plot.enrichment_bar.summary",
                categories);
        return EnrichmentPlotSupport.withSelectionMetrics(result, table.rows().size(), selected.size(),
                request.string("selectionMode"));
    }
}

final class PlotEnrichmentBubbleTool extends AbstractOpenTool {
    private static final ToolDescriptor DESCRIPTOR = PlotToolSupport.descriptor(
            "plot.enrichment-bubble",
            "Enrichment Bubble Plot",
            "Plot fold enrichment versus significance with hit count as bubble size.",
            List.of(
                    ToolParameter.requiredFile("input", "Enrichment table", "Output from GO/KEGG enrichment."),
                    ToolParameter.string("foldColumn", "Fold-enrichment column", "Fold-enrichment column.", "fold_enrichment"),
                    ToolParameter.string("adjustedPColumn", "Adjusted P column", "Adjusted P-value column.", "adjusted_p_value"),
                    ToolParameter.string("hitCountColumn", "Hit-count column", "Hit-count column.", "hits"),
                    ToolParameter.integer("maxTerms", "Maximum terms", "Maximum bubbles plotted.", 100, 1, 1000),
                    EnrichmentPlotSupport.selectionParameter()
            ),
            "Enrichment"
    );

    @Override
    public ToolDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public ValidationResult validate(ToolRequest request) { return PlotToolSupport.validate(request); }

    @Override
    public TaskResult execute(ToolRequest request, TaskContext context) throws Exception {
        var table = PlotToolSupport.readTable(request.path("input"), null);
        int fold = table.requiredColumn(request.string("foldColumn"));
        int p = table.requiredColumn(request.string("adjustedPColumn"));
        int hits = table.requiredColumn(request.string("hitCountColumn"));
        GenericPlotData.GenericSeries.Builder series = new GenericPlotData.GenericSeries.Builder("Enrichment");
        List<EnrichmentPlotSupport.SelectedRow> selected = EnrichmentPlotSupport.selectRows(table, p, request);
        selected.forEach(selectedRow -> {
            List<String> row = selectedRow.row();
            double value = Math.max(0, selectedRow.adjustedP());
            series.add(PlotToolSupport.number(row, fold),
                    value <= 0 ? 300 : -Math.log10(value),
                    PlotToolSupport.number(row, hits));
        });
        GenericPlotData data = new GenericPlotData("BUBBLE", "Fold enrichment",
                "-log10 adjusted P", List.of(), List.of(series.build()));
        TaskResult result = PlotToolSupport.renderGeneric(this, request, context, data,
                "enrichment-bubble", request.string("title"), "result.plot.enrichment_bubble.summary",
                table.rows().size());
        return EnrichmentPlotSupport.withSelectionMetrics(result, table.rows().size(), selected.size(),
                request.string("selectionMode"));
    }
}
