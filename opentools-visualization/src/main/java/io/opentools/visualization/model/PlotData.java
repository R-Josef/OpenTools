package io.opentools.visualization.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = HeatmapData.class, name = "heatmap"),
        @JsonSubTypes.Type(value = VennData.class, name = "venn"),
        @JsonSubTypes.Type(value = GeneStructureData.class, name = "gene-structure"),
        @JsonSubTypes.Type(value = GenericPlotData.class, name = "generic")
})
public sealed interface PlotData permits HeatmapData, VennData, GeneStructureData, GenericPlotData {
}
