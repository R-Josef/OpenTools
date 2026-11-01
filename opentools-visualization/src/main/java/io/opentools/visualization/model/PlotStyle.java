package io.opentools.visualization.model;

import java.util.List;

public record PlotStyle(
        String backgroundColor,
        String foregroundColor,
        String mutedColor,
        String accentColor,
        String missingColor,
        List<String> palette,
        String fontFamily,
        int baseFontSize,
        double lineWidth,
        boolean showLegend,
        boolean showValues,
        Double valueMinimum,
        Double valueMaximum,
        String normalization,
        int rowLabelEvery,
        int columnLabelEvery
) {
    public static final List<String> SEQUENTIAL_BLUE = List.of(
            "#F7FBFF", "#DEEBF7", "#9ECAE1", "#4292C6", "#2171B5", "#08306B"
    );
    public static final List<String> VIRIDIS = List.of(
            "#440154", "#3B528B", "#21918C", "#5EC962", "#FDE725"
    );
    public static final List<String> BLUE_WHITE_RED = List.of(
            "#2166AC", "#67A9CF", "#F7F7F7", "#EF8A62", "#B2182B"
    );

    public PlotStyle {
        backgroundColor = color(backgroundColor, "#FFFFFF");
        foregroundColor = color(foregroundColor, "#1F2933");
        mutedColor = color(mutedColor, "#66737D");
        accentColor = color(accentColor, "#1F6F78");
        missingColor = color(missingColor, "#D8DEE3");
        palette = palette == null || palette.isEmpty() ? SEQUENTIAL_BLUE : List.copyOf(palette);
        fontFamily = fontFamily == null || fontFamily.isBlank() ? "SansSerif" : fontFamily;
        if (baseFontSize < 8 || baseFontSize > 48) {
            throw new IllegalArgumentException("baseFontSize must be between 8 and 48");
        }
        if (lineWidth <= 0 || lineWidth > 12) {
            throw new IllegalArgumentException("lineWidth must be between 0 and 12");
        }
        if ((valueMinimum == null) != (valueMaximum == null)) {
            throw new IllegalArgumentException("valueMinimum and valueMaximum must be set together");
        }
        if (valueMinimum != null && (!Double.isFinite(valueMinimum) || !Double.isFinite(valueMaximum)
                || valueMaximum <= valueMinimum)) {
            throw new IllegalArgumentException("Plot value range is invalid");
        }
        normalization = normalization == null || normalization.isBlank() ? "NONE" : normalization;
        rowLabelEvery = Math.max(1, rowLabelEvery);
        columnLabelEvery = Math.max(1, columnLabelEvery);
    }

    public static PlotStyle defaults() {
        return new PlotStyle(
                "#FFFFFF",
                "#1F2933",
                "#66737D",
                "#1F6F78",
                "#D8DEE3",
                SEQUENTIAL_BLUE,
                "SansSerif",
                13,
                1.2,
                true,
                false,
                null,
                null,
                "NONE",
                1,
                1
        );
    }

    public static PlotStyle heatmap(List<String> palette, boolean showValues) {
        PlotStyle base = defaults();
        return new PlotStyle(
                base.backgroundColor(),
                base.foregroundColor(),
                base.mutedColor(),
                base.accentColor(),
                base.missingColor(),
                palette,
                base.fontFamily(),
                base.baseFontSize(),
                base.lineWidth(),
                true,
                showValues,
                null,
                null,
                "NONE",
                1,
                1
        );
    }

    private static String color(String value, String fallback) {
        String selected = value == null || value.isBlank() ? fallback : value.trim();
        if (!selected.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("Plot colors must use #RRGGBB: " + selected);
        }
        return selected.toUpperCase(java.util.Locale.ROOT);
    }
}
