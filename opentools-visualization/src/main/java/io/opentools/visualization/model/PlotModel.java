package io.opentools.visualization.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record PlotModel(
        String formatVersion,
        String createdAt,
        String sourceTool,
        PlotSpec spec,
        Map<String, String> lineage
) {
    public PlotModel {
        formatVersion = formatVersion == null || formatVersion.isBlank() ? "1.0" : formatVersion;
        sourceTool = sourceTool == null ? "" : sourceTool;
        spec = Objects.requireNonNull(spec, "spec");
        lineage = lineage == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(lineage));
        createdAt = createdAt == null || createdAt.isBlank() ? Instant.now().toString() : createdAt;
    }

    public static PlotModel create(String sourceTool, PlotSpec spec, Map<String, String> lineage) {
        return new PlotModel("1.0", Instant.now().toString(), sourceTool, spec, lineage);
    }
}
