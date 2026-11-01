package io.opentools.visualization.render;

import io.opentools.visualization.model.PlotSpec;
import io.opentools.visualization.model.VennData;
import io.opentools.visualization.scene.PlotScene;
import io.opentools.visualization.scene.PlotSceneBuilder;
import io.opentools.visualization.scene.ScenePoint;
import io.opentools.visualization.scene.SceneText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VennRenderer {
    private VennRenderer() {
    }

    public static PlotScene render(PlotSpec spec, VennData data) {
        if (spec.plotType() == io.opentools.visualization.model.PlotType.VENN && data.setNames().size() <= 3) {
            return renderVenn(spec, data);
        }
        return renderUpSet(spec, data);
    }

    private static PlotScene renderVenn(PlotSpec spec, VennData data) {
        int width = spec.export().width();
        int height = spec.export().height();
        double fontSize = spec.style().baseFontSize();
        PlotSceneBuilder scene = new PlotSceneBuilder(width, height, spec.style().backgroundColor());
        title(scene, spec, 28, fontSize);
        double centerX = width / 2.0;
        double centerY = height / 2.0 + 22;
        double radius = Math.min(
                width * 0.23,
                height * (data.setNames().size() == 2 ? 0.27 : 0.22)
        );
        double[][] centers = data.setNames().size() == 2
                ? new double[][]{{centerX - radius * 0.43, centerY}, {centerX + radius * 0.43, centerY}}
                : new double[][]{
                {centerX - radius * 0.46, centerY - radius * 0.35},
                {centerX + radius * 0.46, centerY - radius * 0.35},
                {centerX, centerY + radius * 0.58}
        };
        Map<String, Integer> intersectionCounts = new LinkedHashMap<>();
        Map<String, String> tooltips = new LinkedHashMap<>();
        for (VennData.Intersection intersection : data.intersections()) {
            String key = key(intersection.sets());
            intersectionCounts.put(key, intersection.count());
            tooltips.put(key, intersectionTooltip(intersection));
        }
        for (int index = 0; index < data.setNames().size(); index++) {
            String color = setColor(index);
            scene.circle(
                    centers[index][0],
                    centers[index][1],
                    radius,
                    color,
                    color,
                    1.4,
                    0.30,
                    data.setNames().get(index) + " (" + data.setSizes().get(index) + ")"
            );
        }
        for (int index = 0; index < data.setNames().size(); index++) {
            double x = centers[index][0];
            double y = centers[index][1] - radius - 20;
            if (data.setNames().size() == 3) {
                y = switch (index) {
                    case 0 -> centers[index][1] - radius - 12;
                    case 1 -> centers[index][1] - radius - 12;
                    default -> centers[index][1] + radius + 26;
                };
            }
            scene.text(
                    x,
                    y,
                    data.setNames().get(index) + " (" + data.setSizes().get(index) + ")",
                    spec.style().foregroundColor(),
                    fontSize,
                    true,
                    SceneText.TextAnchor.CENTER,
                    0,
                    ""
            );
        }

        Map<String, double[]> labelPositions = new LinkedHashMap<>();
        if (data.setNames().size() == 2) {
            String first = data.setNames().get(0);
            String second = data.setNames().get(1);
            labelPositions.put(key(List.of(first)), new double[]{centerX - radius * 1.02, centerY});
            labelPositions.put(key(List.of(second)), new double[]{centerX + radius * 1.02, centerY});
            labelPositions.put(key(List.of(first, second)), new double[]{centerX, centerY + 8});
        } else {
            String a = data.setNames().get(0);
            String b = data.setNames().get(1);
            String c = data.setNames().get(2);
            labelPositions.put(key(List.of(a)), new double[]{centerX - radius * 0.96, centerY - radius * 0.56});
            labelPositions.put(key(List.of(b)), new double[]{centerX + radius * 0.96, centerY - radius * 0.56});
            labelPositions.put(key(List.of(c)), new double[]{centerX, centerY + radius * 1.18});
            labelPositions.put(key(List.of(a, b)), new double[]{centerX, centerY - radius * 0.46});
            labelPositions.put(key(List.of(a, c)), new double[]{centerX - radius * 0.48, centerY + radius * 0.33});
            labelPositions.put(key(List.of(b, c)), new double[]{centerX + radius * 0.48, centerY + radius * 0.33});
            labelPositions.put(key(List.of(a, b, c)), new double[]{centerX, centerY + radius * 0.18});
        }
        intersectionCounts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed())
                .forEach(entry -> {
                    double[] position = labelPositions.get(entry.getKey());
                    if (position != null) {
                        scene.text(
                                position[0],
                                position[1],
                                Integer.toString(entry.getValue()),
                                spec.style().foregroundColor(),
                                fontSize + 2,
                                true,
                                SceneText.TextAnchor.CENTER,
                                0,
                                tooltips.getOrDefault(entry.getKey(), "")
                        );
                    }
                });
        return scene.build();
    }

    private static PlotScene renderUpSet(PlotSpec spec, VennData data) {
        int width = Math.max(spec.export().width(), 720);
        double fontSize = spec.style().baseFontSize();
        int setCount = data.setNames().size();
        List<VennData.Intersection> intersections = data.intersections().stream()
                .sorted(Comparator.comparingInt(VennData.Intersection::count).reversed()
                        .thenComparing(intersection -> key(intersection.sets())))
                .limit(data.maximumIntersections())
                .toList();
        double left = 230;
        double right = 30;
        double top = spec.title().isBlank() ? 30 : 82;
        double barHeight = 210;
        double matrixTop = top + barHeight + 32;
        double matrixHeight = Math.max(110, setCount * 30);
        double height = Math.max(spec.export().height(), matrixTop + matrixHeight + 70);
        double plotWidth = width - left - right;
        double columnWidth = Math.min(82, plotWidth / Math.max(1, intersections.size()));
        int maximum = intersections.stream().mapToInt(VennData.Intersection::count).max().orElse(1);

        PlotSceneBuilder scene = new PlotSceneBuilder(width, (int) Math.ceil(height), spec.style().backgroundColor());
        title(scene, spec, 28, fontSize);
        scene.line(left, top + barHeight, width - right, top + barHeight, spec.style().mutedColor(), 1);
        for (int setIndex = 0; setIndex < setCount; setIndex++) {
            double y = matrixTop + setIndex * 30 + 15;
            int setSize = data.setSizes().get(setIndex);
            double barWidth = plotWidth * 0.23 * (setSize / (double) Math.max(1, data.setSizes().stream().mapToInt(Integer::intValue).max().orElse(1)));
            scene.text(
                    left - 15,
                    y + 5,
                    data.setNames().get(setIndex),
                    spec.style().foregroundColor(),
                    fontSize,
                    true,
                    SceneText.TextAnchor.RIGHT,
                    0,
                    data.setNames().get(setIndex) + ": " + setSize
            );
            scene.rect(
                    16,
                    y - 8,
                    Math.max(2, barWidth),
                    16,
                    spec.style().palette().get(setIndex % spec.style().palette().size()),
                    "",
                    0,
                    1,
                    data.setNames().get(setIndex) + ": " + setSize
            );
            scene.text(
                    20,
                    y + 4,
                    Integer.toString(setSize),
                    PlotColors.contrast(spec.style().palette().get(setIndex % spec.style().palette().size())),
                    fontSize - 2,
                    false,
                    SceneText.TextAnchor.LEFT,
                    0,
                    data.setNames().get(setIndex) + ": " + setSize
            );
        }
        for (int index = 0; index < intersections.size(); index++) {
            VennData.Intersection intersection = intersections.get(index);
            double centerX = left + (index + 0.5) * columnWidth;
            double countHeight = barHeight * intersection.count() / Math.max(1, maximum);
            scene.rect(
                    centerX - columnWidth * 0.32,
                    top + barHeight - countHeight,
                    columnWidth * 0.64,
                    countHeight,
                    spec.style().accentColor(),
                    "",
                    0,
                    1,
                    intersectionTooltip(intersection)
            );
            scene.text(
                    centerX,
                    top + barHeight - countHeight - 8,
                    Integer.toString(intersection.count()),
                    spec.style().foregroundColor(),
                    fontSize - 1,
                    true,
                    SceneText.TextAnchor.CENTER,
                    0,
                    intersectionTooltip(intersection)
            );
            List<Integer> selectedRows = new ArrayList<>();
            for (String setName : intersection.sets()) {
                int setIndex = data.setNames().indexOf(setName);
                if (setIndex >= 0) {
                    selectedRows.add(setIndex);
                }
            }
            if (!selectedRows.isEmpty()) {
                double firstY = matrixTop + selectedRows.getFirst() * 30 + 15;
                double lastY = matrixTop + selectedRows.getLast() * 30 + 15;
                scene.line(
                        centerX,
                        firstY,
                        centerX,
                        lastY,
                        spec.style().mutedColor(),
                        2
                );
            }
            for (int setIndex = 0; setIndex < setCount; setIndex++) {
                double y = matrixTop + setIndex * 30 + 15;
                boolean selected = selectedRows.contains(setIndex);
                scene.circle(
                        centerX,
                        y,
                        selected ? 6.5 : 4,
                        selected ? spec.style().accentColor() : "#D7DDE2",
                        selected ? spec.style().accentColor() : "#B8C1C7",
                        0.8,
                        1,
                        intersectionTooltip(intersection)
                );
            }
            if (columnWidth >= 42) {
                scene.text(
                        centerX,
                        matrixTop + matrixHeight + 24,
                        Integer.toString(index + 1),
                        spec.style().mutedColor(),
                        fontSize - 3,
                        false,
                        SceneText.TextAnchor.CENTER,
                        -55,
                        intersectionTooltip(intersection)
                );
            }
        }
        scene.text(
                16,
                top - 14,
                "Set size",
                spec.style().mutedColor(),
                fontSize - 1,
                true,
                SceneText.TextAnchor.LEFT,
                0,
                ""
        );
        scene.text(
                left,
                top - 14,
                "Intersection size",
                spec.style().mutedColor(),
                fontSize - 1,
                true,
                SceneText.TextAnchor.LEFT,
                0,
                ""
        );
        return scene.build();
    }

    private static void title(PlotSceneBuilder scene, PlotSpec spec, double margin, double fontSize) {
        if (!spec.title().isBlank()) {
            scene.text(
                    margin,
                    margin + fontSize + 6,
                    spec.title(),
                    spec.style().foregroundColor(),
                    fontSize + 7,
                    true,
                    SceneText.TextAnchor.LEFT,
                    0,
                    ""
            );
        }
        if (!spec.subtitle().isBlank()) {
            scene.text(
                    margin,
                    margin + fontSize + 28,
                    spec.subtitle(),
                    spec.style().mutedColor(),
                    fontSize - 1
            );
        }
    }

    private static String key(List<String> sets) {
        List<String> sorted = new ArrayList<>(sets);
        sorted.sort(String::compareTo);
        return String.join("\u0000", sorted);
    }

    private static String setColor(int index) {
        return switch (index % 6) {
            case 0 -> "#3F83B5";
            case 1 -> "#D47A43";
            case 2 -> "#6A9A55";
            case 3 -> "#9B6AAE";
            case 4 -> "#B65A62";
            default -> "#5C7A89";
        };
    }

    private static String intersectionTooltip(VennData.Intersection intersection) {
        String base = String.join(" + ", intersection.sets()) + ": " + intersection.count();
        if (intersection.examples().isEmpty()) {
            return base;
        }
        return base + "\n" + String.join(", ", intersection.examples().stream().limit(8).toList());
    }
}
