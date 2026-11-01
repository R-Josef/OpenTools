package io.opentools.visualization.model;

import java.util.List;

public record PlotExport(
        List<String> formats,
        String baseName,
        int width,
        int height,
        int dpi,
        boolean transparent,
        boolean overwrite
) {
    public PlotExport {
        formats = formats == null || formats.isEmpty()
                ? List.of("SVG", "PNG", "PDF")
                : formats.stream().map(value -> value.toUpperCase(java.util.Locale.ROOT)).distinct().toList();
        for (String format : formats) {
            if (!List.of("SVG", "PNG", "PDF").contains(format)) {
                throw new IllegalArgumentException("Unsupported export format: " + format);
            }
        }
        baseName = baseName == null || baseName.isBlank() ? "plot" : baseName.trim();
        if (width < 320 || width > 20_000 || height < 240 || height > 20_000) {
            throw new IllegalArgumentException("Plot dimensions must be between 320x240 and 20000x20000");
        }
        if (dpi < 72 || dpi > 1200) {
            throw new IllegalArgumentException("DPI must be between 72 and 1200");
        }
    }

    public static PlotExport defaults(String baseName) {
        return new PlotExport(List.of("SVG", "PNG", "PDF"), baseName, 1200, 800, 144, false, false);
    }
}
