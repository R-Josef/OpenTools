package io.opentools.visualization.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record PlotSpec(
        String schemaVersion,
        String plotId,
        PlotType plotType,
        String title,
        String subtitle,
        PlotStyle style,
        PlotExport export,
        Map<String, String> metadata,
        PlotData data
) {
    public PlotSpec {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion;
        plotId = plotId == null || plotId.isBlank() ? "plot" : plotId;
        plotType = Objects.requireNonNull(plotType, "plotType");
        title = title == null ? "" : title;
        subtitle = subtitle == null ? "" : subtitle;
        style = style == null ? PlotStyle.defaults() : style;
        export = export == null ? PlotExport.defaults(plotId) : export;
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        data = Objects.requireNonNull(data, "data");
        validateType(plotType, data);
    }

    public PlotSpec withExport(PlotExport nextExport) {
        return new PlotSpec(schemaVersion, plotId, plotType, title, subtitle, style, nextExport, metadata, data);
    }

    public PlotSpec withData(PlotData nextData) {
        return new PlotSpec(schemaVersion, plotId, plotType, title, subtitle, style, export, metadata, nextData);
    }

    private static void validateType(PlotType type, PlotData data) {
        boolean valid = switch (type) {
            case HEATMAP -> data instanceof HeatmapData;
            case VENN, UPSET -> data instanceof VennData;
            case GENE_STRUCTURE -> data instanceof GeneStructureData;
            case GENERIC -> data instanceof GenericPlotData;
        };
        if (!valid) {
            throw new IllegalArgumentException("Plot type " + type + " is incompatible with " + data.getClass().getSimpleName());
        }
    }
}
