package io.opentools.visualization;

import io.opentools.core.tool.ToolRegistry;
import io.opentools.visualization.task.GeneStructureTool;
import io.opentools.visualization.task.HeatmapTool;
import io.opentools.visualization.task.ExpressionPlotTools;
import io.opentools.visualization.task.EnrichmentPlotTools;
import io.opentools.visualization.task.TreePlotTool;
import io.opentools.visualization.task.ComparativePlotTools;
import io.opentools.visualization.task.AdvancedPlotTools;
import io.opentools.visualization.task.VennTool;

public final class BuiltInVisualizations {
    private BuiltInVisualizations() {
    }

    public static void register(ToolRegistry registry) {
        registry.register(new HeatmapTool());
        registry.register(new VennTool());
        registry.register(new GeneStructureTool());
        ExpressionPlotTools.tools().forEach(registry::register);
        EnrichmentPlotTools.tools().forEach(registry::register);
        registry.register(new TreePlotTool());
        ComparativePlotTools.tools().forEach(registry::register);
        AdvancedPlotTools.tools().forEach(registry::register);
    }
}
