package io.opentools.visualization.scene;

public record SceneLine(
        double x1,
        double y1,
        double x2,
        double y2,
        String stroke,
        double strokeWidth,
        String tooltip
) implements ScenePrimitive {
    public SceneLine {
        strokeWidth = Math.max(0, strokeWidth);
        tooltip = tooltip == null ? "" : tooltip;
    }
}
