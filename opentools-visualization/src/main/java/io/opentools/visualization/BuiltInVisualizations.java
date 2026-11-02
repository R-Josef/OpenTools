package io.opentools.visualization;

import io.opentools.core.tool.ToolRegistry;
import io.opentools.visualization.tools.GeneStructureTool;
import io.opentools.visualization.tools.HeatmapTool;
import io.opentools.visualization.tools.ExpressionPlotTools;
import io.opentools.visualization.tools.EnrichmentPlotTools;
import io.opentools.visualization.tools.TreePlotTool;
import io.opentools.visualization.tools.ComparativePlotTools;
import io.opentools.visualization.tools.AdvancedPlotTools;
import io.opentools.visualization.tools.VennTool;

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
