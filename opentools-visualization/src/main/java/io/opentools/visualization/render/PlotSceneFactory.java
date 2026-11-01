package io.opentools.visualization.render;

import io.opentools.visualization.model.GeneStructureData;
import io.opentools.visualization.model.GenericPlotData;
import io.opentools.visualization.model.HeatmapData;
import io.opentools.visualization.model.PlotSpec;
import io.opentools.visualization.model.VennData;
import io.opentools.visualization.scene.PlotScene;

public final class PlotSceneFactory {
    private PlotSceneFactory() {
    }

    public static PlotScene create(PlotSpec spec) {
        return switch (spec.plotType()) {
            case HEATMAP -> HeatmapRenderer.render(spec, (HeatmapData) spec.data());
            case VENN, UPSET -> VennRenderer.render(spec, (VennData) spec.data());
            case GENE_STRUCTURE -> GeneStructureRenderer.render(spec, (GeneStructureData) spec.data());
            case GENERIC -> GenericPlotRenderer.render(spec, (GenericPlotData) spec.data());
        };
    }
}
