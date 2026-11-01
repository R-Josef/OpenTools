package io.opentools.visualization.render;

import io.opentools.visualization.model.GenericPlotData;
import io.opentools.visualization.model.PlotSpec;
import io.opentools.visualization.scene.PlotScene;
import io.opentools.visualization.scene.PlotSceneBuilder;
import io.opentools.visualization.scene.ScenePoint;
import io.opentools.visualization.scene.SceneText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class GenericPlotRenderer {
    private GenericPlotRenderer() {
    }

    public static PlotScene render(PlotSpec spec, GenericPlotData data) {
        int width = spec.export().width();
        int height = spec.export().height();
        double fontSize = spec.style().baseFontSize();
        PlotSceneBuilder scene = new PlotSceneBuilder(width, height, spec.style().backgroundColor());
        drawTitle(scene, spec, fontSize);
        if ("CIRCOS".equals(data.chart())) {
            drawCircos(scene, spec, data, width, height, fontSize);
            return scene.build();
        }
        double left = 82;
        double right = 32;
        double top = spec.title().isBlank() ? 38 : 88;
        double bottom = 82;
        double plotWidth = width - left - right;
        double plotHeight = height - top - bottom;

        Bounds bounds = bounds(data);
        if (bounds.maxY <= bounds.minY) {
            bounds = new Bounds(bounds.minX, bounds.maxX + 1, bounds.minY, bounds.minY + 1);
        }
        if (bounds.maxX <= bounds.minX) {
            bounds = new Bounds(bounds.minX, bounds.minX + 1, bounds.minY, bounds.maxY);
        }
        scene.line(left, top, left, top + plotHeight, spec.style().foregroundColor(), 1.2);
        scene.line(left, top + plotHeight, left + plotWidth, top + plotHeight, spec.style().foregroundColor(), 1.2);
        for (int tick = 0; tick <= 5; tick++) {
            double y = top + plotHeight - tick * plotHeight / 5.0;
            double value = bounds.minY + tick * (bounds.maxY - bounds.minY) / 5.0;
            scene.line(left, y, left + plotWidth, y, "#E8ECEF", 0.7);
            scene.text(left - 8, y + 4, String.format(Locale.ROOT, "%.4g", value),
                    spec.style().mutedColor(), fontSize - 2, false, SceneText.TextAnchor.RIGHT, 0, "");
        }
        String chart = data.chart();
        switch (chart) {
            case "LINE" -> drawLines(scene, spec, data, left, top, plotWidth, plotHeight, bounds);
            case "BUBBLE" -> drawBubbles(scene, spec, data, left, top, plotWidth, plotHeight, bounds);
            case "BAR" -> drawBars(scene, spec, data, left, top, plotWidth, plotHeight, bounds);
            case "STACKED_BAR" -> drawStackedBars(scene, spec, data, left, top, plotWidth, plotHeight, bounds);
            case "BOX" -> drawBoxes(scene, spec, data, left, top, plotWidth, plotHeight, bounds);
            case "VIOLIN" -> drawViolins(scene, spec, data, left, top, plotWidth, plotHeight, bounds);
            default -> drawScatter(scene, spec, data, left, top, plotWidth, plotHeight, bounds);
        }
        drawCategoryLabels(scene, spec, data, left, top, plotWidth, plotHeight, fontSize);
        drawAxisLabels(scene, spec, data, left, top, plotWidth, plotHeight, fontSize);
        return scene.build();
    }

    private static void drawCircos(
            PlotSceneBuilder scene, PlotSpec spec, GenericPlotData data,
            int width, int height, double fontSize
    ) {
        double cx = width / 2.0;
        double cy = height / 2.0 + 18;
        double radius = Math.min(width, height) * 0.34;
        scene.circle(cx, cy, radius, spec.style().backgroundColor(),
                spec.style().mutedColor(), 1.2, 1, "");
        for (int seriesIndex = 0; seriesIndex < data.series().size(); seriesIndex++) {
            GenericPlotData.GenericSeries series = data.series().get(seriesIndex);
            double max = series.y().stream().mapToDouble(Double::doubleValue).max().orElse(1);
            String color = color(spec, seriesIndex);
            for (int index = 0; index < series.x().size(); index++) {
                double startAngle = series.x().get(index) / Math.max(1, max) * Math.PI * 2;
                double endAngle = series.y().get(index) / Math.max(1, max) * Math.PI * 2;
                double arcRadius = radius * (0.62 + 0.05 * (seriesIndex % 5));
                int steps = Math.max(2, (int) Math.ceil(Math.abs(endAngle - startAngle) * 30));
                List<ScenePoint> points = new ArrayList<>();
                for (int step = 0; step <= steps; step++) {
                    double angle = startAngle + (endAngle - startAngle) * step / steps;
                    points.add(new ScenePoint(cx + Math.cos(angle) * (arcRadius + 8),
                            cy + Math.sin(angle) * (arcRadius + 8)));
                }
                for (int step = steps; step >= 0; step--) {
                    double angle = startAngle + (endAngle - startAngle) * step / steps;
                    points.add(new ScenePoint(cx + Math.cos(angle) * (arcRadius - 8),
                            cy + Math.sin(angle) * (arcRadius - 8)));
                }
                scene.polygon(points, color, "", 0, series.name() + " "
                        + format(series.x().get(index)) + "-" + format(series.y().get(index)));
            }
            double angle = seriesIndex * 2 * Math.PI / Math.max(1, data.series().size());
            scene.text(cx + Math.cos(angle) * (radius + 28), cy + Math.sin(angle) * (radius + 28),
                    series.name(), color, fontSize - 2, true, SceneText.TextAnchor.CENTER, 0, "");
        }
    }

    private static void drawScatter(
            PlotSceneBuilder scene, PlotSpec spec, GenericPlotData data,
            double left, double top, double width, double height, Bounds bounds
    ) {
        for (int seriesIndex = 0; seriesIndex < data.series().size(); seriesIndex++) {
            GenericPlotData.GenericSeries series = data.series().get(seriesIndex);
            String color = color(spec, seriesIndex);
            for (int index = 0; index < series.x().size(); index++) {
                double x = left + normalize(series.x().get(index), bounds.minX, bounds.maxX) * width;
                double y = top + height - normalize(series.y().get(index), bounds.minY, bounds.maxY) * height;
                String tooltip = series.name() + " x=" + format(series.x().get(index))
                        + " y=" + format(series.y().get(index));
                scene.circle(x, y, 4.5, color, "#FFFFFF", 0.7, 0.88, tooltip);
            }
        }
    }

    private static void drawLines(
            PlotSceneBuilder scene, PlotSpec spec, GenericPlotData data,
            double left, double top, double width, double height, Bounds bounds
    ) {
        for (int seriesIndex = 0; seriesIndex < data.series().size(); seriesIndex++) {
            GenericPlotData.GenericSeries series = data.series().get(seriesIndex);
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < series.x().size(); i++) {
                order.add(i);
            }
            order.sort(Comparator.comparingDouble(i -> series.x().get(i)));
            double previousX = 0;
            double previousY = 0;
            for (int index = 0; index < order.size(); index++) {
                int pointIndex = order.get(index);
                double x = left + normalize(series.x().get(pointIndex), bounds.minX, bounds.maxX) * width;
                double y = top + height - normalize(series.y().get(pointIndex), bounds.minY, bounds.maxY) * height;
                if (index > 0) {
                    scene.line(previousX, previousY, x, y, color(spec, seriesIndex), spec.style().lineWidth());
                }
                previousX = x;
                previousY = y;
            }
        }
    }

    private static void drawBubbles(
            PlotSceneBuilder scene, PlotSpec spec, GenericPlotData data,
            double left, double top, double width, double height, Bounds bounds
    ) {
        double maxSize = data.series().stream().flatMap(series -> series.sizes().stream())
                .mapToDouble(Double::doubleValue).max().orElse(1);
        for (int seriesIndex = 0; seriesIndex < data.series().size(); seriesIndex++) {
            GenericPlotData.GenericSeries series = data.series().get(seriesIndex);
            String color = color(spec, seriesIndex);
            for (int index = 0; index < series.x().size(); index++) {
                double x = left + normalize(series.x().get(index), bounds.minX, bounds.maxX) * width;
                double y = top + height - normalize(series.y().get(index), bounds.minY, bounds.maxY) * height;
                double radius = 3 + 13 * Math.sqrt(Math.max(0, series.sizes().get(index)) / Math.max(1, maxSize));
                String tooltip = series.name() + " x=" + format(series.x().get(index))
                        + " y=" + format(series.y().get(index))
                        + " size=" + format(series.sizes().get(index));
                scene.circle(x, y, radius, color, "#FFFFFF", 0.7, 0.55, tooltip);
            }
        }
    }

    private static void drawBars(
            PlotSceneBuilder scene, PlotSpec spec, GenericPlotData data,
            double left, double top, double width, double height, Bounds bounds
    ) {
        int categories = Math.max(1, data.categories().size());
        int seriesCount = Math.max(1, data.series().size());
        double groupWidth = width / categories;
        double barWidth = groupWidth * 0.72 / seriesCount;
        double zeroY = top + height - normalize(0, bounds.minY, bounds.maxY) * height;
        for (int seriesIndex = 0; seriesIndex < data.series().size(); seriesIndex++) {
            GenericPlotData.GenericSeries series = data.series().get(seriesIndex);
            for (int index = 0; index < series.x().size(); index++) {
                double category = Math.max(0, Math.min(categories - 1, Math.round(series.x().get(index))));
                double value = series.y().get(index);
                double y = top + height - normalize(value, bounds.minY, bounds.maxY) * height;
                double x = left + category * groupWidth + (groupWidth - barWidth * seriesCount) / 2
                        + seriesIndex * barWidth;
                scene.rect(x, Math.min(y, zeroY), barWidth * 0.92, Math.max(1, Math.abs(zeroY - y)),
                        color(spec, seriesIndex), "#FFFFFF", 0.4, 0.86,
                        series.name() + " " + categoryLabel(data, (int) category) + "=" + format(value));
            }
        }
    }

    private static void drawStackedBars(
            PlotSceneBuilder scene, PlotSpec spec, GenericPlotData data,
            double left, double top, double width, double height, Bounds bounds
    ) {
        int categories = Math.max(1, data.categories().size());
        double groupWidth = width / categories;
        double barWidth = groupWidth * 0.62;
        double[] totals = new double[categories];
        for (GenericPlotData.GenericSeries series : data.series()) {
            for (int index = 0; index < series.x().size(); index++) {
                int category = Math.max(0, Math.min(categories - 1, (int) Math.round(series.x().get(index))));
                totals[category] += Math.max(0, series.y().get(index));
            }
        }
        double max = Math.max(bounds.maxY, java.util.Arrays.stream(totals).max().orElse(1));
        for (int seriesIndex = 0; seriesIndex < data.series().size(); seriesIndex++) {
            GenericPlotData.GenericSeries series = data.series().get(seriesIndex);
            double[] offsets = new double[categories];
            for (int index = 0; index < series.x().size(); index++) {
                int category = Math.max(0, Math.min(categories - 1, (int) Math.round(series.x().get(index))));
                double value = Math.max(0, series.y().get(index));
                double segmentHeight = value / max * height;
                double y = top + height - (offsets[category] + value) / max * height;
                double x = left + category * groupWidth + (groupWidth - barWidth) / 2;
                scene.rect(x, y, barWidth, Math.max(1, segmentHeight), color(spec, seriesIndex),
                        "#FFFFFF", 0.35, 0.92,
                        series.name() + " " + categoryLabel(data, category) + "=" + format(value));
                offsets[category] += value;
            }
        }
    }

    private static void drawBoxes(
            PlotSceneBuilder scene, PlotSpec spec, GenericPlotData data,
            double left, double top, double width, double height, Bounds bounds
    ) {
        double slot = width / Math.max(1, data.series().size());
        for (int seriesIndex = 0; seriesIndex < data.series().size(); seriesIndex++) {
            List<Double> values = data.series().get(seriesIndex).y().stream().sorted().toList();
            if (values.isEmpty()) {
                continue;
            }
            double q1 = quantile(values, 0.25);
            double median = quantile(values, 0.5);
            double q3 = quantile(values, 0.75);
            double min = values.getFirst();
            double max = values.getLast();
            double centerX = left + (seriesIndex + 0.5) * slot;
            double boxWidth = Math.min(58, slot * 0.55);
            double y1 = mapY(q1, top, height, bounds);
            double y3 = mapY(q3, top, height, bounds);
            scene.rect(centerX - boxWidth / 2, y3, boxWidth, Math.max(1, y1 - y3),
                    color(spec, seriesIndex), spec.style().foregroundColor(), 1, 0.75,
                    data.series().get(seriesIndex).name() + " n=" + values.size());
            scene.line(centerX, mapY(median, top, height, bounds), centerX, mapY(median, top, height, bounds),
                    spec.style().foregroundColor(), 2.4);
            scene.line(centerX, mapY(min, top, height, bounds), centerX, y1,
                    spec.style().mutedColor(), 1);
            scene.line(centerX, y3, centerX, mapY(max, top, height, bounds),
                    spec.style().mutedColor(), 1);
        }
    }

    private static void drawViolins(
            PlotSceneBuilder scene, PlotSpec spec, GenericPlotData data,
            double left, double top, double width, double height, Bounds bounds
    ) {
        double slot = width / Math.max(1, data.series().size());
        for (int seriesIndex = 0; seriesIndex < data.series().size(); seriesIndex++) {
            List<Double> values = data.series().get(seriesIndex).y();
            if (values.isEmpty()) {
                continue;
            }
            double centerX = left + (seriesIndex + 0.5) * slot;
            double halfWidth = Math.min(34, slot * 0.28);
            double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(bounds.minY);
            double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(bounds.maxY);
            List<ScenePoint> points = new ArrayList<>();
            int steps = 40;
            for (int step = 0; step <= steps; step++) {
                double value = min + (max - min) * step / steps;
                double density = density(values, value, Math.max(1e-9, (max - min) / 12));
                points.add(new ScenePoint(centerX + halfWidth * density, mapY(value, top, height, bounds)));
            }
            for (int step = steps; step >= 0; step--) {
                double value = min + (max - min) * step / steps;
                double density = density(values, value, Math.max(1e-9, (max - min) / 12));
                points.add(new ScenePoint(centerX - halfWidth * density, mapY(value, top, height, bounds)));
            }
            scene.polygon(points, color(spec, seriesIndex), spec.style().foregroundColor(), 0.8,
                    data.series().get(seriesIndex).name() + " n=" + values.size());
        }
    }

    private static void drawCategoryLabels(
            PlotSceneBuilder scene, PlotSpec spec, GenericPlotData data,
            double left, double top, double width, double height, double fontSize
    ) {
        if (!List.of("BAR", "BOX", "VIOLIN", "STACKED_BAR").contains(data.chart())) {
            return;
        }
        int count = Math.max(data.categories().size(), data.series().size());
        if (count == 0) {
            return;
        }
        double slot = width / count;
        for (int index = 0; index < count; index++) {
            String label = !data.categories().isEmpty() && index < data.categories().size()
                    ? data.categories().get(index)
                    : data.series().size() > index ? data.series().get(index).name() : Integer.toString(index + 1);
            if (label.length() > 22) {
                label = label.substring(0, 19) + "...";
            }
            scene.text(left + (index + 0.5) * slot, top + height + 24, label,
                    spec.style().mutedColor(), fontSize - 2, false, SceneText.TextAnchor.CENTER, -35, "");
        }
    }

    private static void drawAxisLabels(
            PlotSceneBuilder scene, PlotSpec spec, GenericPlotData data,
            double left, double top, double width, double height, double fontSize
    ) {
        scene.text(left + width / 2, top + height + 68, data.xLabel(),
                spec.style().foregroundColor(), fontSize, true, SceneText.TextAnchor.CENTER, 0, "");
        scene.text(18, top + height / 2, data.yLabel(),
                spec.style().foregroundColor(), fontSize, true, SceneText.TextAnchor.CENTER, -90, "");
    }

    private static void drawTitle(PlotSceneBuilder scene, PlotSpec spec, double fontSize) {
        if (!spec.title().isBlank()) {
            scene.text(28, 34, spec.title(), spec.style().foregroundColor(), fontSize + 7, true);
        }
        if (!spec.subtitle().isBlank()) {
            scene.text(28, 55, spec.subtitle(), spec.style().mutedColor(), fontSize - 1);
        }
    }

    private static Bounds bounds(GenericPlotData data) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (GenericPlotData.GenericSeries series : data.series()) {
            for (int index = 0; index < series.x().size(); index++) {
                double x = series.x().get(index);
                double y = series.y().get(index);
                if (Double.isFinite(x)) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                }
                if (Double.isFinite(y)) {
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (minY == Double.POSITIVE_INFINITY) {
            minY = 0;
            maxY = 1;
        }
        if (minX == Double.POSITIVE_INFINITY) {
            minX = 0;
            maxX = Math.max(1, data.categories().size() - 1);
        }
        if ("BAR".equals(data.chart()) || "STACKED_BAR".equals(data.chart())) {
            minY = Math.min(0, minY);
        }
        return new Bounds(minX, maxX, minY, maxY);
    }

    private static double normalize(double value, double min, double max) {
        return max <= min ? 0 : (value - min) / (max - min);
    }

    private static double mapY(double value, double top, double height, Bounds bounds) {
        return top + height - normalize(value, bounds.minY, bounds.maxY) * height;
    }

    private static String color(PlotSpec spec, int index) {
        return spec.style().palette().get(index % spec.style().palette().size());
    }

    private static String categoryLabel(GenericPlotData data, int index) {
        return index >= 0 && index < data.categories().size() ? data.categories().get(index) : Integer.toString(index + 1);
    }

    private static double quantile(List<Double> values, double fraction) {
        double position = (values.size() - 1) * fraction;
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        double weight = position - lower;
        return values.get(lower) * (1 - weight) + values.get(upper) * weight;
    }

    private static double density(List<Double> values, double point, double bandwidth) {
        double sum = 0;
        for (double value : values) {
            double z = (point - value) / bandwidth;
            sum += Math.exp(-0.5 * z * z);
        }
        return Math.min(1, sum / Math.max(1, values.size()) * 2.5);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4g", value);
    }

    private record Bounds(double minX, double maxX, double minY, double maxY) {
    }
}
