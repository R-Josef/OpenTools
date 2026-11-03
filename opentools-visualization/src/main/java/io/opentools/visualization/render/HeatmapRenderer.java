package io.opentools.visualization.render;

import io.opentools.visualization.model.HeatmapData;
import io.opentools.visualization.model.PlotSpec;
import io.opentools.visualization.scene.PlotScene;
import io.opentools.visualization.scene.PlotSceneBuilder;
import io.opentools.visualization.scene.SceneText;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HeatmapRenderer {
    private static final java.util.List<String> GROUP_COLORS = java.util.List.of(
            "#1F6F78", "#D47A43", "#5F7DB5", "#7F9E56", "#B55C78", "#8C6BAA"
    );

    private HeatmapRenderer() {
    }

    public static PlotScene render(PlotSpec spec, HeatmapData data) {
        double fontSize = spec.style().baseFontSize();
        double right = spec.style().showLegend() ? 150 : 38;
        Map<Integer, String> sampleGroups = sampleGroups(spec, data);
        double top = titleTop(spec, fontSize) + (sampleGroups.isEmpty() ? 0 : 26);
        double rowLabelFontSize = fontSize - 2;
        double maximumRowLabelWidth = data.rowLabels().stream()
                .mapToDouble(label -> TextMetrics.width(label, false, rowLabelFontSize))
                .max()
                .orElse(100);
        double left = Math.max(130, maximumRowLabelWidth + 24);
        int width = Math.max(
                520,
                (int) Math.ceil(Math.max(spec.export().width(), left + right + 320))
        );
        double maximumColumnLabelHeight = data.columnLabels().stream()
                .mapToDouble(label -> TextMetrics.width(label, false, fontSize - 2)
                        * Math.sin(Math.toRadians(55)))
                .max()
                .orElse(70);
        double bottom = Math.max(112, maximumColumnLabelHeight + 64);
        int height = Math.max(
                360,
                (int) Math.ceil(Math.max(spec.export().height(), top + bottom + 220))
        );
        double plotWidth = Math.max(80, width - left - right);
        double plotHeight = Math.max(80, height - top - bottom);
        double cellWidth = plotWidth / data.columnLabels().size();
        double cellHeight = plotHeight / data.rowLabels().size();
        ValueRange range = valueRange(spec, data);
        int rowEvery = Math.max(
                spec.style().rowLabelEvery(),
                (int) Math.ceil(data.rowLabels().size() / Math.max(1.0, plotHeight / 19.0))
        );
        int columnEvery = Math.max(
                spec.style().columnLabelEvery(),
                (int) Math.ceil(data.columnLabels().size() / Math.max(1.0, plotWidth / 66.0))
        );

        PlotSceneBuilder scene = new PlotSceneBuilder(width, height, spec.style().backgroundColor());
        title(scene, spec, fontSize);
        drawSampleGroups(scene, spec, sampleGroups, left, top, cellWidth, fontSize);
        for (int row = 0; row < data.rowLabels().size(); row++) {
            double y = top + row * cellHeight;
            if (row % rowEvery == 0) {
                scene.text(
                        left - 10,
                        y + cellHeight * 0.68,
                        data.rowLabels().get(row),
                        spec.style().foregroundColor(),
                        rowLabelFontSize,
                        false,
                        SceneText.TextAnchor.RIGHT,
                        0,
                        data.rowLabels().get(row)
                );
            }
            for (int column = 0; column < data.columnLabels().size(); column++) {
                Double value = data.values().get(row).get(column);
                String fill = value == null
                        ? spec.style().missingColor()
                        : PlotColors.sample(
                                spec.style().palette(),
                                (value - range.minimum) / (range.maximum - range.minimum)
                        );
                String tooltip = data.rowLabels().get(row) + " / " + data.columnLabels().get(column)
                        + ": " + (value == null ? "NA" : String.format(Locale.ROOT, "%.6g", value));
                scene.rect(
                        left + column * cellWidth,
                        y,
                        cellWidth + 0.25,
                        cellHeight + 0.25,
                        fill,
                        "",
                        0,
                        1,
                        tooltip
                );
                if (spec.style().showValues() && value != null && cellWidth >= 25 && cellHeight >= 17) {
                    scene.text(
                            left + (column + 0.5) * cellWidth,
                            y + cellHeight * 0.64,
                            String.format(Locale.ROOT, "%.2f", value),
                            PlotColors.contrast(fill),
                            Math.min(fontSize - 3, cellHeight * 0.55),
                            false,
                            SceneText.TextAnchor.CENTER,
                            0,
                            tooltip
                    );
                }
            }
        }

        for (int column = 0; column < data.columnLabels().size(); column++) {
            if (column % columnEvery != 0) {
                continue;
            }
            scene.text(
                    left + (column + 0.5) * cellWidth,
                    top + plotHeight + 14,
                    data.columnLabels().get(column),
                    spec.style().mutedColor(),
                    fontSize - 2,
                    false,
                    SceneText.TextAnchor.RIGHT,
                    -55,
                    data.columnLabels().get(column)
            );
        }
        scene.line(left, top + plotHeight, left + plotWidth, top + plotHeight, spec.style().mutedColor(), 1);
        scene.line(left, top, left, top + plotHeight, spec.style().mutedColor(), 1);
        drawLegend(scene, spec, range, left + plotWidth + 38, top, plotHeight);

        scene.text(
                24,
                height - 20,
                data.rowLabels().size() + " rows x " + data.columnLabels().size() + " columns"
                        + (data.sourceRows() > data.rowLabels().size()
                        || data.sourceColumns() > data.columnLabels().size() ? " (sampled for display)" : ""),
                spec.style().mutedColor(),
                fontSize - 2
        );
        return scene.build();
    }

    private static Map<Integer, String> sampleGroups(PlotSpec spec, HeatmapData data) {
        Map<Integer, String> groups = new LinkedHashMap<>();
        for (int column = 0; column < data.columnLabels().size(); column++) {
            String group = spec.metadata().get("sampleGroup." + column);
            if (group != null && !group.isBlank()) {
                groups.put(column, group);
            }
        }
        return groups;
    }

    private static void drawSampleGroups(
            PlotSceneBuilder scene,
            PlotSpec spec,
            Map<Integer, String> groups,
            double left,
            double top,
            double cellWidth,
            double fontSize
    ) {
        if (groups.isEmpty()) {
            return;
        }
        List<String> palette = GROUP_COLORS;
        Map<String, Integer> colorIndex = new LinkedHashMap<>();
        int start = 0;
        int count = groups.size();
        while (start < count) {
            int column = start;
            String group = groups.get(column);
            int end = column;
            while (end + 1 < count && group.equals(groups.get(end + 1))) {
                end++;
            }
            int paletteIndex = colorIndex.computeIfAbsent(group, ignored -> colorIndex.size());
            String fill = palette.get(paletteIndex % Math.max(1, palette.size()));
            scene.rect(
                    left + column * cellWidth,
                    top - 22,
                    (end - column + 1) * cellWidth,
                    16,
                    fill,
                    "",
                    0,
                    1,
                    "Tissue: " + group
            );
            scene.text(
                    left + (column + end + 1) * cellWidth / 2.0,
                    top - 9,
                    group,
                    PlotColors.contrast(fill),
                    fontSize - 2,
                    false,
                    SceneText.TextAnchor.CENTER,
                    0,
                    "Tissue: " + group
            );
            start = end + 1;
        }
    }

    private static void drawLegend(
            PlotSceneBuilder scene,
            PlotSpec spec,
            ValueRange range,
            double x,
            double top,
            double plotHeight
    ) {
        if (!spec.style().showLegend()) {
            return;
        }
        double legendHeight = Math.min(250, plotHeight * 0.55);
        int steps = 100;
        for (int step = 0; step < steps; step++) {
            String color = PlotColors.sample(spec.style().palette(), 1.0 - step / (double) (steps - 1));
            scene.rect(x, top + step * legendHeight / steps, 18, legendHeight / steps + 1, color, "", 0);
        }
        for (int step = 0; step <= 4; step++) {
            double value = range.maximum - (range.maximum - range.minimum) * step / 4.0;
            scene.text(
                    x + 25,
                    top + step * legendHeight / 4 + 4,
                    String.format(Locale.ROOT, "%.2f", value),
                    spec.style().mutedColor(),
                    spec.style().baseFontSize() - 3
            );
        }
        scene.text(
                x,
                top - 12,
                spec.style().normalization().equals("ROW_ZSCORE") ? "Row z-score" : "Value",
                spec.style().foregroundColor(),
                spec.style().baseFontSize() - 2,
                true,
                SceneText.TextAnchor.LEFT,
                0,
                ""
        );
    }

    private static void title(PlotSceneBuilder scene, PlotSpec spec, double fontSize) {
        if (!spec.title().isBlank()) {
            scene.text(
                    24,
                    24 + fontSize + 7,
                    spec.title(),
                    spec.style().foregroundColor(),
                    fontSize + 7,
                    true
            );
        }
        if (!spec.subtitle().isBlank()) {
            scene.text(
                    24,
                    24 + fontSize + 29,
                    spec.subtitle(),
                    spec.style().mutedColor(),
                    fontSize - 1
            );
        }
    }

    private static double titleTop(PlotSpec spec, double fontSize) {
        if (!spec.title().isBlank() && !spec.subtitle().isBlank()) {
            return 88;
        }
        if (!spec.title().isBlank()) {
            return 68;
        }
        return 38;
    }

    private static ValueRange valueRange(PlotSpec spec, HeatmapData data) {
        if (spec.style().valueMinimum() != null) {
            return new ValueRange(spec.style().valueMinimum(), spec.style().valueMaximum());
        }
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (List<Double> row : data.values()) {
            for (Double value : row) {
                if (value != null) {
                    minimum = Math.min(minimum, value);
                    maximum = Math.max(maximum, value);
                }
            }
        }
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)) {
            return new ValueRange(0, 1);
        }
        return minimum == maximum
                ? new ValueRange(minimum - 0.5, maximum + 0.5)
                : new ValueRange(minimum, maximum);
    }

    private record ValueRange(double minimum, double maximum) {
    }
}
